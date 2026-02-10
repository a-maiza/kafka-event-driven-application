package com.example.payment;

import com.example.common.TopicNames;
import com.example.common.avro.OrderCreated;
import com.example.common.avro.OrderLine;
import com.example.common.avro.PaymentAuthorized;
import com.example.common.avro.PaymentFailed;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentServiceIT {

    static Network network = Network.newNetwork();

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            "confluentinc/cp-kafka:7.4.1")
            .withListener("kafka:19092")
            .withNetwork(network);

    static GenericContainer<?> schemaRegistry;

    @BeforeAll
    static void startSchemaRegistry() throws Exception {
        schemaRegistry = new GenericContainer<>(
                DockerImageName.parse("confluentinc/cp-schema-registry:7.4.1"))
                .withNetwork(network)
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "kafka:19092")
                .withExposedPorts(8081)
                .waitingFor(Wait.forHttp("/subjects").forStatusCode(200))
                .dependsOn(kafka);
        schemaRegistry.start();

        // Set compatibility to NONE so multiple Avro types can share payments.v1 topic
        HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(schemaRegistryUrl() + "/config"))
                        .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibility\":\"NONE\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @AfterAll
    static void stopSchemaRegistry() {
        if (schemaRegistry != null) {
            schemaRegistry.stop();
        }
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.properties.schema.registry.url",
                PaymentServiceIT::schemaRegistryUrl);
        registry.add("spring.kafka.producer.properties.schema.registry.url",
                PaymentServiceIT::schemaRegistryUrl);
    }

    static String schemaRegistryUrl() {
        return "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081);
    }

    private KafkaProducer<String, OrderCreated> createAvroProducer() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class,
                KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl()
        );
        return new KafkaProducer<>(props);
    }

    private KafkaConsumer<String, SpecificRecordBase> createAvroConsumer(String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class,
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl(),
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true
        );
        KafkaConsumer<String, SpecificRecordBase> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));
        return consumer;
    }

    private KafkaConsumer<String, byte[]> createBytesConsumer(String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-dlq-consumer-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class
        );
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));
        return consumer;
    }

    private OrderCreated buildOrderCreated(String orderId, String total) {
        return OrderCreated.newBuilder()
                .setId(orderId)
                .setCustomerId("customer-test")
                .setLines(List.of(new OrderLine("SKU-001", 1)))
                .setTotal(total)
                .setStatus("CREATED")
                .setCreatedAt(Instant.now().toString())
                .build();
    }

    @Test
    @Order(1)
    void shouldProducePaymentAuthorizedForSmallOrder() throws Exception {
        String orderId = "order-small-" + UUID.randomUUID();
        OrderCreated event = buildOrderCreated(orderId, "500.00");

        try (KafkaProducer<String, OrderCreated> producer = createAvroProducer();
             KafkaConsumer<String, SpecificRecordBase> consumer = createAvroConsumer(TopicNames.PAYMENTS)) {

            producer.send(new ProducerRecord<>(TopicNames.ORDERS, orderId, event)).get();

            ConsumerRecord<String, SpecificRecordBase> record = pollForRecordByKey(consumer, orderId, 20_000);
            assertThat(record).isNotNull();
            assertThat(record.value()).isInstanceOf(PaymentAuthorized.class);

            PaymentAuthorized authorized = (PaymentAuthorized) record.value();
            assertThat(authorized.getOrderId()).isEqualTo(orderId);
            assertThat(authorized.getAmount()).isEqualTo("500.00");
        }
    }

    @Test
    @Order(2)
    void shouldProducePaymentFailedForLargeOrder() throws Exception {
        String orderId = "order-large-" + UUID.randomUUID();
        OrderCreated event = buildOrderCreated(orderId, "1500.00");

        try (KafkaProducer<String, OrderCreated> producer = createAvroProducer();
             KafkaConsumer<String, SpecificRecordBase> consumer = createAvroConsumer(TopicNames.PAYMENTS)) {

            producer.send(new ProducerRecord<>(TopicNames.ORDERS, orderId, event)).get();

            ConsumerRecord<String, SpecificRecordBase> record = pollForRecordByKey(consumer, orderId, 20_000);
            assertThat(record).isNotNull();
            assertThat(record.value()).isInstanceOf(PaymentFailed.class);

            PaymentFailed failed = (PaymentFailed) record.value();
            assertThat(failed.getOrderId()).isEqualTo(orderId);
            assertThat(failed.getReason()).contains("1500.00");
        }
    }

    @Test
    @Order(3)
    void shouldSendToDlqOnDeserializationError() throws Exception {
        String dlqTopic = TopicNames.ORDERS + "-dlt";

        try (KafkaConsumer<String, byte[]> dlqConsumer = createBytesConsumer(dlqTopic)) {
            // Send invalid Avro bytes to orders topic
            Map<String, Object> props = Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class
            );
            try (KafkaProducer<String, byte[]> rawProducer = new KafkaProducer<>(props)) {
                rawProducer.send(new ProducerRecord<>(TopicNames.ORDERS,
                        "bad-order", "invalid-avro-data".getBytes())).get();
            }

            // DLQ message should arrive after retries
            ConsumerRecord<String, byte[]> dlqRecord = pollForRecordByKey(dlqConsumer, "bad-order", 30_000);
            assertThat(dlqRecord).isNotNull();
        }
    }

    private <V> ConsumerRecord<String, V> pollForRecordByKey(
            KafkaConsumer<String, V> consumer, String key, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, V> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, V> record : records) {
                if (record.key().equals(key)) {
                    return record;
                }
            }
        }
        return null;
    }

}
