package com.example.streams.topology;

import com.example.common.TopicNames;
import com.example.common.avro.OrderStatusChanged;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class StatusCountsTopologyTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, OrderStatusChanged> inputTopic;

    @BeforeEach
    void setUp() {
        MockSchemaRegistryClient schemaRegistryClient = new MockSchemaRegistryClient();

        SpecificAvroSerde<OrderStatusChanged> valueSerde = new SpecificAvroSerde<>(schemaRegistryClient);
        valueSerde.configure(Map.of("schema.registry.url", "mock://test"), false);

        StreamsBuilder builder = new StreamsBuilder();
        builder.<String, OrderStatusChanged>stream(TopicNames.ORDER_STATUS,
                        Consumed.with(Serdes.String(), valueSerde))
                .groupBy((key, value) -> value.getFinalStatus(),
                        Grouped.with(Serdes.String(), valueSerde))
                .count(Materialized.as(StatusCountsTopology.STATUS_COUNTS_STORE));

        Topology topology = builder.build();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        props.put("schema.registry.url", "mock://test");

        testDriver = new TopologyTestDriver(topology, props);

        inputTopic = testDriver.createInputTopic(
                TopicNames.ORDER_STATUS,
                new StringSerializer(),
                valueSerde.serializer());
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    private OrderStatusChanged buildEvent(String orderId, String finalStatus) {
        return OrderStatusChanged.newBuilder()
                .setOrderId(orderId)
                .setPaymentStatus("AUTHORIZED")
                .setInventoryStatus("RESERVED")
                .setFinalStatus(finalStatus)
                .setUpdatedAt("2025-01-01T00:00:00Z")
                .build();
    }

    @Test
    void shouldCountByFinalStatus() {
        inputTopic.pipeInput("order-1", buildEvent("order-1", "CONFIRMED"));
        inputTopic.pipeInput("order-2", buildEvent("order-2", "CONFIRMED"));
        inputTopic.pipeInput("order-3", buildEvent("order-3", "CONFIRMED"));
        inputTopic.pipeInput("order-4", buildEvent("order-4", "REJECTED"));
        inputTopic.pipeInput("order-5", buildEvent("order-5", "REJECTED"));

        KeyValueStore<String, Long> store = testDriver.getKeyValueStore(
                StatusCountsTopology.STATUS_COUNTS_STORE);

        assertThat(store.get("CONFIRMED")).isEqualTo(3);
        assertThat(store.get("REJECTED")).isEqualTo(2);
    }

    @Test
    void emptyInput_shouldHaveNoRecordsInStore() {
        KeyValueStore<String, Long> store = testDriver.getKeyValueStore(
                StatusCountsTopology.STATUS_COUNTS_STORE);

        assertThat(store.get("CONFIRMED")).isNull();
        assertThat(store.get("REJECTED")).isNull();
    }
}
