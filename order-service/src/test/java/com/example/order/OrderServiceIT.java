package com.example.order;

import com.example.common.TopicNames;
import com.example.common.avro.OrderCreated;
import com.example.order.controller.dto.CreateOrderRequest;
import com.example.order.controller.dto.OrderLineDto;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class OrderServiceIT {

    static Network network = Network.newNetwork();

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            "confluentinc/cp-kafka:7.4.1")
            .withListener("kafka:19092")
            .withNetwork(network);

    static GenericContainer<?> schemaRegistry;

    @Autowired
    TestRestTemplate restTemplate;

    @BeforeAll
    static void startSchemaRegistry() {
        schemaRegistry = new GenericContainer<>(
                DockerImageName.parse("confluentinc/cp-schema-registry:7.4.1"))
                .withNetwork(network)
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "kafka:19092")
                .withExposedPorts(8081)
                .waitingFor(Wait.forHttp("/subjects").forStatusCode(200))
                .dependsOn(kafka);
        schemaRegistry.start();
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
        registry.add("spring.kafka.producer.properties.schema.registry.url",
                () -> schemaRegistryUrl());
    }

    static String schemaRegistryUrl() {
        return "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081);
    }

    private KafkaConsumer<String, OrderCreated> createConsumer() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class,
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl(),
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true
        );
        KafkaConsumer<String, OrderCreated> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TopicNames.ORDERS));
        return consumer;
    }

    @Test
    void createOrder_shouldPublishOrderCreatedToKafka() {
        try (KafkaConsumer<String, OrderCreated> consumer = createConsumer()) {
            CreateOrderRequest request = new CreateOrderRequest(
                    "customer-1",
                    List.of(new OrderLineDto("SKU-001", 2)),
                    new BigDecimal("99.99"));

            ResponseEntity<Map> response = restTemplate.postForEntity("/orders", request, Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String orderId = (String) response.getBody().get("orderId");

            ConsumerRecord<String, OrderCreated> record = pollForRecord(consumer);
            assertThat(record).isNotNull();
            assertThat(record.key()).isEqualTo(orderId);

            OrderCreated event = record.value();
            assertThat(event.getId()).isEqualTo(orderId);
            assertThat(event.getCustomerId()).isEqualTo("customer-1");
            assertThat(event.getTotal()).isEqualTo("99.99");
            assertThat(event.getStatus()).isEqualTo("CREATED");
            assertThat(event.getLines()).hasSize(1);
        }
    }

    @Test
    void createOrder_shouldSetCorrelationIdHeader() {
        try (KafkaConsumer<String, OrderCreated> consumer = createConsumer()) {
            String correlationId = "test-corr-" + UUID.randomUUID();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Correlation-Id", correlationId);
            headers.set("Content-Type", "application/json");

            CreateOrderRequest request = new CreateOrderRequest(
                    "customer-2",
                    List.of(new OrderLineDto("SKU-002", 1)),
                    new BigDecimal("49.99"));

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "/orders", new HttpEntity<>(request, headers), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String orderId = (String) response.getBody().get("orderId");

            ConsumerRecord<String, OrderCreated> record = pollForRecordByKey(consumer, orderId);
            assertThat(record).isNotNull();

            org.apache.kafka.common.header.Header corrHeader = record.headers().lastHeader("correlationId");
            assertThat(corrHeader).isNotNull();
            assertThat(new String(corrHeader.value(), StandardCharsets.UTF_8)).isEqualTo(correlationId);
        }
    }

    private ConsumerRecord<String, OrderCreated> pollForRecord(
            KafkaConsumer<String, OrderCreated> consumer) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, OrderCreated> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        return null;
    }

    private ConsumerRecord<String, OrderCreated> pollForRecordByKey(
            KafkaConsumer<String, OrderCreated> consumer, String key) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, OrderCreated> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, OrderCreated> record : records) {
                if (record.key().equals(key)) {
                    return record;
                }
            }
        }
        return null;
    }
}
