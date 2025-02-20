open module shrimpworks.mes {
	requires java.base;
	requires java.net.http;
	requires java.desktop;

	requires org.slf4j;
	requires org.slf4j.simple;

	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.dataformat.yaml;

	requires org.apache.commons.pool2;
	requires redis.clients.jedis;

	requires xnio.api;
	requires undertow.core;
}
