package io.wizzie.normalizer.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.utils.MockTime;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.integration.utils.EmbeddedKafkaCluster;
import org.apache.kafka.streams.integration.utils.IntegrationTestUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.wizzie.bootstrapper.builder.Config;
import io.wizzie.normalizer.builder.StreamBuilder;
import io.wizzie.normalizer.exceptions.PlanBuilderException;
import io.wizzie.normalizer.funcs.Function;
import io.wizzie.normalizer.funcs.MapperFunction;
import io.wizzie.normalizer.model.PlanModel;
import io.wizzie.normalizer.serializers.JsonDeserializer;
import io.wizzie.normalizer.serializers.JsonSerde;
import io.wizzie.normalizer.serializers.JsonSerializer;

public class Base64MapperIntegrationTest {
	private final static int NUM_BROKERS = 1;

	@ClassRule
	public static EmbeddedKafkaCluster CLUSTER = new EmbeddedKafkaCluster(NUM_BROKERS);
	private final static MockTime MOCK_TIME = CLUSTER.time;
	private static final int REPLICATION_FACTOR = 1;
	private static final String INPUT_TOPIC = "input1";
	private static final String OUTPUT_TOPIC = "output1";

	@BeforeClass
	public static void startKafkaCluster() throws Exception {
		// inputs
		CLUSTER.createTopic(INPUT_TOPIC, 1, REPLICATION_FACTOR);
		// sinks
		CLUSTER.createTopic(OUTPUT_TOPIC, 1, REPLICATION_FACTOR);
	}

	@Test
	public void diffCounterStoreMapperShouldWork() throws InterruptedException {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		File file = new File(classLoader.getResource("base64-mapper.json").getFile());

		Properties streamsConfiguration = new Properties();

		String appId = UUID.randomUUID().toString();
		streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, appId);
		streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
		streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
		streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class);
		streamsConfiguration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		streamsConfiguration.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 1);

		ObjectMapper objectMapper = new ObjectMapper();

		PlanModel model = null;

		try {
			model = objectMapper.readValue(file, PlanModel.class);
		} catch (IOException e) {
			fail("Exception : " + e.getMessage());
		}

		Config config = new Config();
		config.put(StreamsConfig.APPLICATION_ID_CONFIG, "app-id-1");

		StreamBuilder streamBuilder = new StreamBuilder(config, null);

		KafkaStreams streams = null;

		try {
			streams = new KafkaStreams(streamBuilder.builder(model).build(), streamsConfiguration);
		} catch (PlanBuilderException e) {
			fail("Exception : " + e.getMessage());
		}

		Map<String, Function> functions = streamBuilder.getFunctions("myStream");
		Function myFunction = functions.get("myBase64Mapper");

		assertNotNull(myFunction);
		assertTrue(myFunction instanceof MapperFunction);

		streams.start();

		Map<String, Object> msg1 = new HashMap<>();
		msg1.put("timestamp", 1234567890);
		msg1.put("mac", "hello word");

		KeyValue<String, Map<String, Object>> kvStream1 = new KeyValue<>("KEY_A", msg1);

		Properties producerConfig = new Properties();
		producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
//		producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
		producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
		producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
		producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

		try {
			IntegrationTestUtils.produceKeyValuesSynchronously(INPUT_TOPIC, Collections.singletonList(kvStream1),
					producerConfig, MOCK_TIME);
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		Properties consumerConfigA = new Properties();
		consumerConfigA.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());
		consumerConfigA.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer");
		consumerConfigA.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerConfigA.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

		Map<String, Object> expectedMsg1 = new HashMap<>();
		expectedMsg1.put("timestamp", 1234567890);
		expectedMsg1.put("mac", "aGVsbG8gd29yZA==");

		KeyValue<String, Map<String, Object>> expectedDataKv1 = new KeyValue<>("KEY_A", expectedMsg1);

		List<KeyValue<String, Map>> receivedMessagesFromOutput1 = IntegrationTestUtils
				.waitUntilMinKeyValueRecordsReceived(consumerConfigA, OUTPUT_TOPIC, 1);

		assertEquals(Arrays.asList(expectedDataKv1), receivedMessagesFromOutput1);

		streams.close();
		streamBuilder.close();
	}
}
