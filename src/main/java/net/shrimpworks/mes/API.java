package net.shrimpworks.mes;

import java.beans.ConstructorProperties;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import io.redisearch.Document;
import io.redisearch.Query;
import io.redisearch.SearchResult;
import io.redisearch.client.AddOptions;
import io.redisearch.client.Client;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Options;
import redis.clients.jedis.exceptions.JedisDataException;

public class API implements Closeable {

	private static final Logger logger = LoggerFactory.getLogger(API.class);

	private static final int WORKER_IO_THREADS = 2;
	private static final int WORKER_TASK_CORE_THREADS = 5;

	private static final String HTTP_STATUS = "/status";
	private static final String HTTP_SEARCH = "/search";
	private static final String HTTP_ADD = "/index/add";
	private static final String HTTP_ADD_BATCH = "/index/addBatch";

	private final Undertow server;
	private final Client client;
	private final String allowOrigins;

	public API(InetSocketAddress bindAddress, Client client, String allowOrigin, String submissionToken) {
		this.client = client;
		this.allowOrigins = allowOrigin;

		Predicate tokenCheck = ex -> {
			String auth = Optional.ofNullable(ex.getRequestHeaders().getFirst("Authorization")).orElse("");
			return (auth.equals(submissionToken) || auth.equals("bearer " + submissionToken));
		};

		HttpHandler handlers = Handlers.routing()
									   .add("GET", HTTP_STATUS, statusHandler())
									   .add("GET", HTTP_SEARCH, searchHandler())
									   .add("OPTIONS", HTTP_SEARCH, corsOptionsHandler("GET, OPTIONS"))
									   .add("POST", HTTP_ADD, orUnauthorised(tokenCheck, addHandler()))
									   .add("POST", HTTP_ADD_BATCH, orUnauthorised(tokenCheck, addBatchHandler()));

		// provides deflate and gzip encoding on handlers it wraps
		HttpHandler encodingHandler = new EncodingHandler.Builder().build(null).wrap(handlers);

		this.server = Undertow.builder()
							  .setWorkerOption(Options.WORKER_IO_THREADS, WORKER_IO_THREADS)
							  .setWorkerOption(Options.WORKER_TASK_CORE_THREADS, WORKER_TASK_CORE_THREADS)
							  .setWorkerOption(Options.WORKER_TASK_MAX_THREADS, WORKER_TASK_CORE_THREADS)
							  .setWorkerOption(Options.TCP_NODELAY, true)
							  .setSocketOption(Options.REUSE_ADDRESSES, true)
							  .addHttpListener(bindAddress.getPort(), bindAddress.getHostString())
							  .setHandler(encodingHandler)
							  .build();
		this.server.start();

		logger.info("Server started on host {}", bindAddress);
	}

	private HttpHandler orUnauthorised(Predicate predicate, HttpHandler handler) {
		return Handlers.predicate(predicate, handler, ResponseCodeHandler.HANDLE_403);
	}

	@Override
	public void close() {
		this.server.stop();
	}

	private HttpHandler corsOptionsHandler(String methods) {
		return (exchange) -> {
			corsHeaders(exchange, methods);
			exchange.getResponseSender().close();
		};
	}

	private void corsHeaders(HttpServerExchange exchange, String methods) {
		exchange.getResponseHeaders()
				.put(new HttpString("Access-Control-Allow-Origin"), allowOrigins)
				.put(new HttpString("Access-Control-Allow-Methods"), methods);
	}

	private HttpHandler statusHandler() {

		return (exchange) -> {
			final String body = "ok";

			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");

			exchange.dispatch(() -> {
				try {
					exchange.getResponseSender().send(body);
				} finally {
					exchange.endExchange();
				}
			});
		};
	}

	private HttpHandler searchHandler() {
		return (exchange) -> {
			final String query = exchange.getQueryParameters().getOrDefault("q", new ArrayDeque<>(Set.of(""))).getFirst();
			final int offset = Integer.parseInt(exchange.getQueryParameters().getOrDefault("offset", new ArrayDeque<>(Set.of("0")))
														.getFirst());
			final int limit = Integer.parseInt(exchange.getQueryParameters().getOrDefault("limit", new ArrayDeque<>(Set.of("10")))
													   .getFirst());

			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
			corsHeaders(exchange, "GET, OPTIONS");

			// attempt to serve serve from storage, otherwise hit the monitor directly
			exchange.dispatch(() -> {
				logger.info("Searching for query {}", query);
				try {
					SearchResult searchResult = client.search(new Query(query).limit(offset, limit));
					exchange.getResponseSender().send(
						JacksonMapper.JSON.string(SearchResults.fromSearchResult(searchResult, offset, limit))
					);
				} catch (JedisDataException e) {
					logger.error("Search failure", e);
					exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
				} catch (IOException e) {
					logger.error("Failed to process request", e);
					exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
				} finally {
					exchange.endExchange();
				}
			});
		};
	}

	private HttpHandler addBatchHandler() {
		return (exchange) -> {
			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

			exchange.dispatch(() -> {
				logger.info("Adding document batch to the index");
				exchange.startBlocking();
				try {
					AddRequest req = JacksonMapper.JSON.object(exchange.getInputStream(), AddRequest.class);
					Document[] docs = req.docs.stream()
											  .map(AddDocument::toDocument)
											  .toArray(Document[]::new);
					boolean[] results = client.addDocuments(new AddOptions().setReplacementPolicy(AddOptions.ReplacementPolicy.PARTIAL),
															docs);
					int ok = 0;
					for (boolean b : results) if (b) ok++;

					exchange.getResponseSender().send(JacksonMapper.JSON.string(ok));
				} catch (JsonParseException e) {
					exchange.setStatusCode(StatusCodes.BAD_REQUEST);
				} catch (IOException e) {
					logger.error("Failed to process request", e);
					exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
				} finally {
					exchange.endExchange();
				}
			});
		};
	}

	private HttpHandler addHandler() {
		return (exchange) -> {
			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

			exchange.dispatch(() -> {
				logger.info("Adding document to the index");
				exchange.startBlocking();
				try {
					Document doc = JacksonMapper.JSON.object(exchange.getInputStream(), AddDocument.class).toDocument();
					boolean result = client.addDocument(doc, new AddOptions().setReplacementPolicy(AddOptions.ReplacementPolicy.PARTIAL));

					exchange.getResponseSender().send(JacksonMapper.JSON.string(result));
				} catch (JsonParseException e) {
					logger.error("Failed to parse JSON", e);
					exchange.setStatusCode(StatusCodes.BAD_REQUEST);
				} catch (IOException e) {
					logger.error("Failed to process request", e);
					exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
				} finally {
					exchange.endExchange();
				}
			});
		};
	}

	public static class AddRequest {

		public final List<AddDocument> docs;

		@ConstructorProperties("docs")
		public AddRequest(List<AddDocument> docs) {
			this.docs = docs;
		}
	}

	public static class AddDocument {

		public final String id;
		public final Map<String, Object> fields;
		@JsonProperty(defaultValue = "1")
		public final double score;
		public final byte[] payload;

		@ConstructorProperties({ "id", "fields", "score", "payload" })
		public AddDocument(String id, Map<String, Object> fields, double score, byte[] payload) {
			this.id = id;
			this.fields = fields;
			this.score = score;
			this.payload = payload;
		}

		public Document toDocument() {
			return new Document(id, fields, score, payload);
		}

		public static AddDocument fromDocument(Document doc) {
			Map<String, Object> fields = new HashMap<>();
			doc.getProperties().forEach(e -> fields.put(e.getKey(), e.getValue()));
			return new AddDocument(
				doc.getId(),
				fields,
				doc.getScore(),
				doc.getPayload()
			);
		}
	}

	public static class SearchResults {

		public final List<AddDocument> docs;
		public final long totalResults;
		public final int offset;
		public final int limit;

		public SearchResults(List<AddDocument> docs, long totalResults, int offset, int limit) {
			this.docs = docs;
			this.totalResults = totalResults;
			this.offset = offset;
			this.limit = limit;
		}

		public static SearchResults fromSearchResult(SearchResult result, int offset, int limit) {
			return new SearchResults(
				result.docs.stream().map(AddDocument::fromDocument).collect(Collectors.toList()),
				result.totalResults,
				offset, limit);
		}
	}
}
