package net.shrimpworks.mes;

import java.beans.ConstructorProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.redisearch.Schema;
import io.redisearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.exceptions.JedisDataException;

public class Main {

	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			System.err.println("Config file path not provided.");
			System.err.println("Here is an example configuration to get started:");
			sampleConfig();
			System.exit(2);
		}

		Path configPath = Paths.get(args[0]).toAbsolutePath();
		if (!Files.exists(configPath) || !Files.isRegularFile(configPath)) {
			System.err.printf("Config file %s does not exist%n", configPath.toString());
			System.exit(3);
		}

		Config config = JacksonMapper.YAML.object(configPath, Config.class);
		String[] redis = config.redisearch.split(":");
		Client client = new Client(config.index, redis[0], redis.length > 1 ? Integer.parseInt(redis[1]) : 6379);
		try {
			client.createIndex(config.schema.toSchema(), Client.IndexOptions.defaultOptions());
		} catch (JedisDataException je) {
			if (je.getMessage().contains("already exists")) {
				// if the index already exists, we can make an attempt at adding fields (there's no api for deleting fields)
				List<List<Object>> fields = (List<List<Object>>)client.getInfo().get("fields");
				Set<String> fieldNames = fields.stream()
											   .map(f -> new String((byte[])f.get(0), StandardCharsets.UTF_8))
											   .collect(Collectors.toSet());
				Schema.Field[] newFields = config.schema.fields.stream()
															   .filter(f -> !fieldNames.contains(f.name))
															   .map(RediSearchField::toField)
															   .toArray(Schema.Field[]::new);
				// TODO if any change in fields, client.dropIndex(keepDocuments) - needs to be implemented in client
				if (newFields.length > 0) {
					logger.info("Adding new fields to index: {}", Arrays.stream(newFields).map(f -> f.name).collect(Collectors.joining()));
					client.alterIndex(newFields);
				}
			} else {
				throw je;
			}
		}

		// web service startup
		String[] bind = config.bindAddress.split(":");
		API api = new API(InetSocketAddress.createUnresolved(bind[0], Integer.parseInt(bind[1])), client, config.corsAllowOrigins,
						  config.submissionToken);

		// close running services
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			client.close();
			api.close();
		}));
	}

	public static void sampleConfig() throws IOException {
		Config config = new Config("example", "localhost:6379", "0.0.0.0:8080", "*", UUID.randomUUID().toString(),
								   new RediSearchSchema(Set.of(
									   new RediSearchField(Schema.FieldType.FullText, "title", true, false, 5.0, false, null),
									   new RediSearchField(Schema.FieldType.FullText, "body", false, false, 1.0, false, null),
									   new RediSearchField(Schema.FieldType.Numeric, "price", true, true, 1.0, false, null),
									   new RediSearchField(Schema.FieldType.Tag, "tags", false, false, 2.5, false, ",")
								   )));
		System.out.println(JacksonMapper.YAML.string(config));
	}

	public static class Config {

		public final String index;
		public final String redisearch;
		public final String bindAddress;
		public final String corsAllowOrigins;
		public final String submissionToken;
		public final RediSearchSchema schema;

		@ConstructorProperties({ "index", "redisearch", "bindAddress", "corsAllowOrigin", "submissionToken", "schema" })
		public Config(String index, String redisearch, String bindAddress, String corsAllowOrigin, String submissionToken,
					  RediSearchSchema schema) {
			this.index = index;
			this.redisearch = redisearch;
			this.bindAddress = bindAddress;
			this.corsAllowOrigins = corsAllowOrigin;
			this.submissionToken = submissionToken;
			this.schema = schema;
		}
	}

	public static class RediSearchSchema {

		public Set<RediSearchField> fields;

		@ConstructorProperties("fields")
		public RediSearchSchema(Set<RediSearchField> fields) {
			this.fields = fields;
		}

		public Schema toSchema() {
			Schema schema = new Schema();
			fields.forEach(f -> schema.addField(f.toField()));
			return schema;
		}
	}

	public static class RediSearchField {

		public final Schema.FieldType type;
		public final String name;

		public final boolean sortable;
		public final boolean noIndex;

		@JsonProperty(defaultValue = "1")
		public final double weight;
		public final boolean noStem;

		@JsonInclude(value = JsonInclude.Include.NON_EMPTY, content = JsonInclude.Include.NON_NULL)
		public final String separator;

		@ConstructorProperties({ "type", "name", "sortable", "noIndex", "weight", "noStem", "separator" })
		public RediSearchField(
			Schema.FieldType type, String name, boolean sortable, boolean noIndex, double weight, boolean noStem, String separator
		) {
			this.type = type;
			this.name = name;
			this.sortable = sortable;
			this.noIndex = noIndex;
			this.weight = weight;
			this.noStem = noStem;
			this.separator = separator;
		}

		public Schema.Field toField() {
			switch (type) {
				case FullText:
					return new Schema.TextField(name, weight, sortable, noStem, noIndex);
				case Tag:
					return new Schema.TagField(name, separator, sortable);
				case Numeric:
				case Geo:
				default:
					return new Schema.Field(name, type, sortable, noIndex);
			}
		}
	}
}
