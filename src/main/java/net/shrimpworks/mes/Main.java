package net.shrimpworks.mes;

import java.io.IOException;
import java.io.PrintStream;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.IndexDefinition;
import redis.clients.jedis.search.IndexOptions;
import redis.clients.jedis.search.Schema;

public class Main {

	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			System.err.println("Config file path not provided.");
			System.err.println("Here is an example configuration to get started:");
			sampleConfig(System.out);
			System.exit(2);
		}

		Path configPath = Paths.get(args[0]).toAbsolutePath();
		if (!Files.exists(configPath) || !Files.isRegularFile(configPath)) {
			logger.error("Config file {} does not exist", configPath);
			System.exit(3);
		} else {
			logger.info("Using configuration in file {}", configPath);
		}

		Config config = JacksonMapper.YAML.object(configPath, Config.class);
		JedisPooled client = new JedisPooled(
			HostAndPort.from(config.redisHost),
			DefaultJedisClientConfig.builder().timeoutMillis(config.redisTimeoutMillis).build()
		);
		try {
			client.ftCreate(
				config.index,
				IndexOptions.defaultOptions().setDefinition(new IndexDefinition().setPrefixes(config.prefix)),
				config.schema.toSchema()
			);
			logger.info("Created index {}", config.index);
		} catch (JedisDataException je) {
			if (je.getMessage().contains("already exists")) {
				logger.info("Index {} already exists, updating", config.index);
				// if the index already exists, we can make an attempt at adding fields (there's no api for deleting fields)
				List<List<Object>> fields = (List<List<Object>>)client.ftInfo(config.index).get("attributes");
				Set<String> fieldNames = fields.stream()
											   .map(f -> (String)f.get(1))
											   .collect(Collectors.toSet());
				Schema.Field[] newFields = config.schema.fields.stream()
															   .filter(f -> !fieldNames.contains(f.name))
															   .map(RediSearchField::toField)
															   .toArray(Schema.Field[]::new);
				if (newFields.length > 0) {
					logger.info("Adding new fields to index: {}", Arrays.stream(newFields).map(f -> f.name).collect(Collectors.joining()));
					client.ftAlter(config.index, newFields);
				}
			} else {
				throw je;
			}
		}

		// web service startup
		API api = new API(config, client);

		// close running services
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			client.close();
			api.close();
		}));
	}

	public static void sampleConfig(PrintStream out) throws IOException {
		Config config = new Config("example", "ex:", "localhost:6379", 5000, "0.0.0.0:8080", "", "*", UUID.randomUUID().toString(),
								   new RediSearchSchema(Set.of(
									   new RediSearchField(Schema.FieldType.TEXT, "title", true, false, 5.0, false, null),
									   new RediSearchField(Schema.FieldType.TEXT, "body", false, false, 1.0, false, null),
									   new RediSearchField(Schema.FieldType.NUMERIC, "price", true, true, 1.0, false, null),
									   new RediSearchField(Schema.FieldType.TAG, "tags", false, false, 2.5, false, ",")
								   )));
		out.println(JacksonMapper.YAML.string(config));
	}

	public record Config(
		String index,
		String prefix,
		String redisHost,
		int redisTimeoutMillis,
		String bindAddress,
		String rootPath,
		String corsAllowOrigins,
		String submissionToken,
		RediSearchSchema schema
	) {}

	public record RediSearchSchema(
		Set<RediSearchField> fields
	) {

		public Schema toSchema() {
			Schema schema = new Schema();
			fields.forEach(f -> schema.addField(f.toField()));
			return schema;
		}
	}

	public record RediSearchField(
		Schema.FieldType type,
		String name,
		boolean sortable,
		boolean noIndex,
		@JsonProperty(defaultValue = "1")
		double weight,
		boolean noStem,
		@JsonInclude(value = JsonInclude.Include.NON_EMPTY, content = JsonInclude.Include.NON_NULL)
		String separator
	) {

		public Schema.Field toField() {
			return switch (type) {
				case TEXT -> new Schema.TextField(name, weight, sortable, noStem, noIndex);
				case TAG -> new Schema.TagField(name, separator, sortable);
				default /* Numeric, Geo */ -> new Schema.Field(name, type, sortable, noIndex);
			};
		}
	}
}
