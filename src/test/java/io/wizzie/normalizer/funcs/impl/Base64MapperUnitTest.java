package io.wizzie.normalizer.funcs.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.wizzie.bootstrapper.builder.Config;
import io.wizzie.normalizer.builder.StreamBuilder;
import io.wizzie.normalizer.exceptions.PlanBuilderException;
import io.wizzie.normalizer.funcs.Function;
import io.wizzie.normalizer.funcs.MapperFunction;
import io.wizzie.normalizer.model.PlanModel;

public class Base64MapperUnitTest {

	static Config config = new Config();

	static {
		config.put(StreamsConfig.APPLICATION_ID_CONFIG, "app-id-1");
	}

	private static StreamBuilder streamBuilder = new StreamBuilder(config, null);

	@BeforeClass
	public static void initTest() throws IOException, PlanBuilderException {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		File file = new File(classLoader.getResource("base64-mapper.json").getFile());

		ObjectMapper objectMapper = new ObjectMapper();
		PlanModel model = objectMapper.readValue(file, PlanModel.class);
		streamBuilder.builder(model);
	}

	@Test
	public void building() {
		Map<String, Function> functions = streamBuilder.getFunctions("myStream");
		Function myFunc = functions.get("myBase64Mapper");

		assertNotNull(myFunc);
		assertTrue(myFunc instanceof MapperFunction);
		Base64Mapper myBase64SFunction = (Base64Mapper) myFunc;

		assertEquals("mac", myBase64SFunction.dimension);
	}

	@Test
	public void processSimpleMessage() {
		Map<String, Function> functions = streamBuilder.getFunctions("myStream");
		Function myFunc = functions.get("myBase64Mapper");

		assertNotNull(myFunc);
		assertTrue(myFunc instanceof MapperFunction);
		Base64Mapper myBase64SFunction = (Base64Mapper) myFunc;

		Map<String, Object> msg = new HashMap<>();
		msg.put("timestamp", 1234567890);
		msg.put("mac", "hello word");

		Map<String, Object> expectedMsg = new HashMap<>();
		expectedMsg.put("timestamp", 1234567890);
		expectedMsg.put("mac", "aGVsbG8gd29yZA==");

		assertEquals(new KeyValue<>("KEY-A", expectedMsg), myBase64SFunction.process("KEY-A", msg));
	}

	@Test
	public void processNullKey() {
		Map<String, Function> functions = streamBuilder.getFunctions("myStream");
		Function myFunc = functions.get("myBase64Mapper");

		assertNotNull(myFunc);
		assertTrue(myFunc instanceof MapperFunction);
		Base64Mapper myBase64Function = (Base64Mapper) myFunc;

		Map<String, Object> msg = new HashMap<>();
		msg.put("timestamp", 1234567890);
		msg.put("mac", "hello word");

		Map<String, Object> expectedMsg = new HashMap<>();
		expectedMsg.put("timestamp", 1234567890);
		expectedMsg.put("mac", "aGVsbG8gd29yZA==");

		assertEquals(new KeyValue<>(null, expectedMsg), myBase64Function.process(null, msg));
	}

	@Test
	public void processNullMessages() {
		Map<String, Function> functions = streamBuilder.getFunctions("myStream");
		Function myFunc = functions.get("myBase64Mapper");

		assertNotNull(myFunc);
		assertTrue(myFunc instanceof MapperFunction);
		Base64Mapper myBase64Function = (Base64Mapper) myFunc;

		assertEquals(new KeyValue<>("key", null), myBase64Function.process("key", null));
	}

	@Test
	public void processNullDimensionMessages() {
		Map<String, Function> functions = streamBuilder.getFunctions("myStream");
		Function myFunc = functions.get("myBase64Mapper");

		assertNotNull(myFunc);
		assertTrue(myFunc instanceof MapperFunction);
		Base64Mapper myBase64Function = (Base64Mapper) myFunc;

		Map<String, Object> msg = new HashMap<>();
		msg.put("timestamp", 1234567890);

		Map<String, Object> expectedMsg = new HashMap<>();
		expectedMsg.put("timestamp", 1234567890);

		assertEquals(new KeyValue<>(null, expectedMsg), myBase64Function.process(null, msg));
	}

}
