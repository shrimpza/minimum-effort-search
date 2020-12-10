package net.shrimpworks.mes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MainTest {

	@Test
	public void generateConfig() throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Main.sampleConfig(new PrintStream(os));

		Main.Config config = JacksonMapper.YAML.object(os.toByteArray(), Main.Config.class);
		assertEquals("example", config.index);
	}
}
