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
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class StatusCountsTopologyTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, OrderStatusChanged> inputTopic;
    private Topology topology;

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

        topology = builder.build();

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

    // --- Aggregation Correctness ---

    @Test
    void shouldCountSingleEvent() {
        inputTopic.pipeInput("order-1", buildEvent("order-1", "CONFIRMED"));

        KeyValueStore<String, Long> store = testDriver.getKeyValueStore(
                StatusCountsTopology.STATUS_COUNTS_STORE);

        assertThat(store.get("CONFIRMED")).isEqualTo(1);
    }

    @Test
    void shouldHandleMultipleDistinctStatuses() {
        inputTopic.pipeInput("order-1", buildEvent("order-1", "CONFIRMED"));
        inputTopic.pipeInput("order-2", buildEvent("order-2", "REJECTED"));
        inputTopic.pipeInput("order-3", buildEvent("order-3", "PENDING"));
        inputTopic.pipeInput("order-4", buildEvent("order-4", "CANCELLED"));

        KeyValueStore<String, Long> store = testDriver.getKeyValueStore(
                StatusCountsTopology.STATUS_COUNTS_STORE);

        assertThat(store.get("CONFIRMED")).isEqualTo(1);
        assertThat(store.get("REJECTED")).isEqualTo(1);
        assertThat(store.get("PENDING")).isEqualTo(1);
        assertThat(store.get("CANCELLED")).isEqualTo(1);
    }

    @Test
    void duplicateOrderId_shouldCountEachEvent() {
        inputTopic.pipeInput("order-1", buildEvent("order-1", "CONFIRMED"));
        inputTopic.pipeInput("order-1", buildEvent("order-1", "CONFIRMED"));
        inputTopic.pipeInput("order-1", buildEvent("order-1", "CONFIRMED"));

        KeyValueStore<String, Long> store = testDriver.getKeyValueStore(
                StatusCountsTopology.STATUS_COUNTS_STORE);

        assertThat(store.get("CONFIRMED")).isEqualTo(3);
    }

    @Test
    void sameOrderDifferentStatuses_shouldCountBoth() {
        inputTopic.pipeInput("order-1", buildEvent("order-1", "CONFIRMED"));
        inputTopic.pipeInput("order-1", buildEvent("order-1", "REJECTED"));

        KeyValueStore<String, Long> store = testDriver.getKeyValueStore(
                StatusCountsTopology.STATUS_COUNTS_STORE);

        assertThat(store.get("CONFIRMED")).isEqualTo(1);
        assertThat(store.get("REJECTED")).isEqualTo(1);
    }

    // --- State Store Queries ---

    @Test
    void shouldQueryAllEntriesFromStore() {
        inputTopic.pipeInput("order-1", buildEvent("order-1", "CONFIRMED"));
        inputTopic.pipeInput("order-2", buildEvent("order-2", "CONFIRMED"));
        inputTopic.pipeInput("order-3", buildEvent("order-3", "REJECTED"));
        inputTopic.pipeInput("order-4", buildEvent("order-4", "PENDING"));

        KeyValueStore<String, Long> store = testDriver.getKeyValueStore(
                StatusCountsTopology.STATUS_COUNTS_STORE);

        Map<String, Long> allCounts = new LinkedHashMap<>();
        try (KeyValueIterator<String, Long> iterator = store.all()) {
            while (iterator.hasNext()) {
                KeyValue<String, Long> entry = iterator.next();
                allCounts.put(entry.key, entry.value);
            }
        }

        assertThat(allCounts).hasSize(3);
        assertThat(allCounts).containsEntry("CONFIRMED", 2L);
        assertThat(allCounts).containsEntry("REJECTED", 1L);
        assertThat(allCounts).containsEntry("PENDING", 1L);
    }

    @Test
    void storeApproximateNumEntries_shouldMatchDistinctStatuses() {
        inputTopic.pipeInput("order-1", buildEvent("order-1", "CONFIRMED"));
        inputTopic.pipeInput("order-2", buildEvent("order-2", "REJECTED"));
        inputTopic.pipeInput("order-3", buildEvent("order-3", "CONFIRMED"));
        inputTopic.pipeInput("order-4", buildEvent("order-4", "PENDING"));

        KeyValueStore<String, Long> store = testDriver.getKeyValueStore(
                StatusCountsTopology.STATUS_COUNTS_STORE);

        assertThat(store.approximateNumEntries()).isEqualTo(3);
    }

    // --- Edge Cases ---

    @Test
    void highVolumeEvents_shouldCountAccurately() {
        for (int i = 0; i < 400; i++) {
            inputTopic.pipeInput("order-" + i, buildEvent("order-" + i, "CONFIRMED"));
        }
        for (int i = 400; i < 700; i++) {
            inputTopic.pipeInput("order-" + i, buildEvent("order-" + i, "REJECTED"));
        }
        for (int i = 700; i < 1000; i++) {
            inputTopic.pipeInput("order-" + i, buildEvent("order-" + i, "PENDING"));
        }

        KeyValueStore<String, Long> store = testDriver.getKeyValueStore(
                StatusCountsTopology.STATUS_COUNTS_STORE);

        assertThat(store.get("CONFIRMED")).isEqualTo(400);
        assertThat(store.get("REJECTED")).isEqualTo(300);
        assertThat(store.get("PENDING")).isEqualTo(300);
    }

    // --- Topology Structure ---

    @Test
    void topologyShouldDescribeExpectedProcessors() {
        String description = topology.describe().toString();

        assertThat(description).contains(TopicNames.ORDER_STATUS);
        assertThat(description).contains(StatusCountsTopology.STATUS_COUNTS_STORE);
    }
}
