package net.shrimpworks.mes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public interface JacksonMapper {

	JacksonMapper JSON = new BaseMapper(new JsonFactory());
	JacksonMapper YAML = new BaseMapper(new YAMLFactory());

	ObjectMapper mapper();

	/**
	 * Register a custom object deserializer.
	 *
	 * @param <T>          wtf
	 * @param type         type to deserialise
	 * @param deserializer custom deserialiser implementation
	 */
	<T> void addDeserializer(Class<T> type, JsonDeserializer<? extends T> deserializer);

	/**
	 * Register a custom object serializer.
	 *
	 * @param <T>        wtf
	 * @param type       type to serialise
	 * @param serializer custom serialiser implementation
	 */
	<T> void addSerializer(Class<T> type, JsonSerializer<T> serializer);

	/**
	 * Serialise an Object instance to a String.
	 *
	 * @param object object instance to be serialised
	 * @return JSON string
	 * @throws IOException json exception
	 */
	default String string(Object object) throws IOException {
		return mapper().writeValueAsString(object);
	}

	/**
	 * Serialise an Object instance to a String.
	 *
	 * @param object object instance to be serialised
	 * @return JSON string
	 * @throws IOException json exception
	 */
	default String prettyString(Object object) throws IOException {
		return mapper().writerWithDefaultPrettyPrinter().writeValueAsString(object);
	}

	/**
	 * Convert a JSON string to a JsonNode.
	 *
	 * @param stringNode JSON string to be converted to a JSON Node
	 * @return a JSON node
	 * @throws IOException json exception
	 */
	default JsonNode node(String stringNode) throws IOException {
		return mapper().readTree(stringNode);
	}

	/**
	 * Convert a file containing JSON data to a JsonNode.
	 *
	 * @param file JSON file
	 * @return a JSON node
	 * @throws IOException json exception
	 */
	default JsonNode node(File file) throws IOException {
		return mapper().readTree(file);
	}

	/**
	 * Convert an arbitrary object to a JsonNode.
	 *
	 * @param object object instance to be converted to a JSON node
	 * @return a JSON node
	 * @throws IOException json exception
	 */
	default JsonNode node(Object object) throws IOException {
		return mapper().valueToTree(object);
	}

	/**
	 * Convert binary data to a JsonNode.
	 *
	 * @param data data to be converted to a JSON Node
	 * @return a JSON node
	 * @throws IOException json exception
	 */
	default JsonNode node(byte[] data) throws IOException {
		return mapper().readTree(data);
	}

	/**
	 * Convert a JsonNode to an instance of an object specified by objectClass.
	 *
	 * @param node        node to be deserialised
	 * @param objectClass class to deserialise node to
	 * @param <T>         wtf
	 * @return an instance of objectClass populated with data from the node
	 * @throws IOException json exception
	 */
	default <T> T object(JsonNode node, Class<T> objectClass) throws IOException {
		return mapper().readValue(node.traverse(), objectClass);
	}

	/**
	 * Convert a JsonNode to an instance of an object defined in the provided type reference.
	 * <p>
	 * This is useful for instantiating objects using generics (lists, maps, etc).
	 *
	 * @param node          node to be deserialised
	 * @param objectTypeRef type reference determining type to deserialise node to
	 * @param <T>           wtf
	 * @return an instance of type specified by typeRef populated with data from the node
	 * @throws IOException json exception
	 */
	default <T> T object(JsonNode node, TypeReference<T> objectTypeRef) throws IOException {
		return mapper().readValue(node.traverse(), objectTypeRef);
	}

	/**
	 * Convert a JSON string to an instance of an object specified by objectClass.
	 *
	 * @param stringObject JSON string to be deserialised
	 * @param objectClass  class to deserialise node to
	 * @param <T>          wtf
	 * @return an instance of objectClass populated with data from the node
	 * @throws IOException json exception
	 */
	default <T> T object(String stringObject, Class<T> objectClass) throws IOException {
		return mapper().readValue(stringObject, objectClass);
	}

	/**
	 * Convert binary data to an instance of an object specified by objectClass.
	 *
	 * @param data        data to be deserialised
	 * @param objectClass class to deserialise node to
	 * @param <T>         wtf
	 * @return an instance of objectClass populated with data from the node
	 * @throws IOException json exception
	 */
	default <T> T object(byte[] data, Class<T> objectClass) throws IOException {
		return mapper().readValue(data, objectClass);
	}

	/**
	 * Read an on-disk file as an instance of an object specified by objectClass.
	 *
	 * @param file        file to load form
	 * @param objectClass class to deserialise file content to
	 * @param <T>         target object type
	 * @return an instance of objectClass populated with data from the file
	 * @throws IOException file reading or json exception
	 */
	default <T> T object(Path file, Class<T> objectClass) throws IOException {
		return object(Files.newInputStream(file), objectClass);
	}

	/**
	 * Read an on-disk file as an instance of an object specified by objectTypeRef.
	 *
	 * @param file          file to load form
	 * @param objectTypeRef class to deserialise file content to
	 * @param <T>           target object type
	 * @return an instance of objectClass populated with data from the file
	 * @throws IOException file reading or json exception
	 */
	default <T> T object(Path file, TypeReference<T> objectTypeRef) throws IOException {
		return mapper().readValue(Files.newInputStream(file), objectTypeRef);
	}

	/**
	 * Read the contents of the provided InputStream as an instance of the object specified.
	 *
	 * @param stream      input stream to read from
	 * @param objectClass class to deserialise stream content to
	 * @param <T>         target object type
	 * @return an instance of objectClass populated with data from the stream
	 * @throws IOException stream read ot json exception
	 */
	default <T> T object(InputStream stream, Class<T> objectClass) throws IOException {
		return mapper().readValue(stream, objectClass);
	}

	/**
	 * Serialise an Object instance to a byte array.
	 *
	 * @param object object instance to be serialised
	 * @return binary data
	 * @throws IOException json exception
	 */
	default byte[] bytes(Object object) throws IOException {
		return mapper().writeValueAsBytes(object);
	}

	default byte[] bytes(JsonNode node) throws JsonProcessingException {
		return mapper().writeValueAsBytes(node);
	}

	static class BaseMapper implements JacksonMapper {

		private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS");
		private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("uuuu-MM-dd");
		private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

		private final ObjectMapper mapper;
		private final SimpleModule module = new SimpleModule();

		BaseMapper(JsonFactory jsonFactory) {
			mapper = new ObjectMapper(jsonFactory);
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
			mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
			mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

			addDeserializer(BigDecimal.class, new BigDecimalDeserializer(mapper));

			addSerializer(LocalDateTime.class, new DateTimeSerializer());
			addDeserializer(LocalDateTime.class, new DateTimeDeserializer(mapper));

			addSerializer(LocalDate.class, new DateSerializer());
			addDeserializer(LocalDate.class, new DateDeserializer(mapper));

			addSerializer(LocalTime.class, new TimeSerializer());
			addDeserializer(LocalTime.class, new TimeDeserializer());

			addDeserializer(Duration.class, new DurationDeserializer());
		}

		@Override
		public ObjectMapper mapper() {
			return mapper;
		}

		@Override
		public <T> void addDeserializer(Class<T> type, JsonDeserializer<? extends T> deserializer) {
			mapper.registerModule(module.addDeserializer(type, deserializer));
		}

		@Override
		public <T> void addSerializer(Class<T> type, JsonSerializer<T> serializer) {
			mapper.registerModule(module.addSerializer(type, serializer));
		}

		private static class BigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

			private final ObjectMapper mapper;

			private BigDecimalDeserializer(ObjectMapper mapper) {
				this.mapper = mapper;
			}

			@Override
			public BigDecimal deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
				jsonParser.setCodec(mapper);
				JsonNode node = jsonParser.readValueAsTree();
				return BigDecimal.valueOf(node.asDouble());
			}
		}

		private static class DateTimeSerializer extends JsonSerializer<LocalDateTime> {

			@Override
			public void serialize(LocalDateTime value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
				jgen.writeString(value.format(DATE_TIME_FMT));
			}
		}

		private static class DateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

			private final ObjectMapper mapper;

			public DateTimeDeserializer(ObjectMapper mapper) {
				this.mapper = mapper;
			}

			@Override
			public LocalDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
				jsonParser.setCodec(mapper);
				JsonNode node = jsonParser.readValueAsTree();
				return LocalDateTime.parse(node.asText(), DATE_TIME_FMT);
			}
		}

		private static class DateSerializer extends JsonSerializer<LocalDate> {

			@Override
			public void serialize(LocalDate value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
				jgen.writeString(value.format(DATE_FMT));
			}
		}

		private static class DateDeserializer extends JsonDeserializer<LocalDate> {

			private final ObjectMapper mapper;

			public DateDeserializer(ObjectMapper mapper) {
				this.mapper = mapper;
			}

			@Override
			public LocalDate deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
				jsonParser.setCodec(mapper);
				JsonNode node = jsonParser.readValueAsTree();
				return LocalDate.parse(node.asText(), DATE_FMT);
			}
		}

		private static class TimeSerializer extends JsonSerializer<LocalTime> {

			@Override
			public void serialize(LocalTime value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
				jgen.writeString(value.format(TIME_FMT));
			}
		}

		private static class TimeDeserializer extends JsonDeserializer<LocalTime> {

			@Override
			public LocalTime deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
				return LocalTime.parse(jp.getText(), TIME_FMT);
			}
		}

		private static class DurationDeserializer extends JsonDeserializer<Duration> {

			@Override
			public Duration deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
				String val = jp.getText();
				if (val.contains(" ")) {
					String[] parts = val.split(" ");
					return Duration.of(Long.parseLong(parts[0]), ChronoUnit.valueOf(parts[1].toUpperCase()));
				} else {
					return Duration.parse(val);
				}
			}
		}

	}

}
