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
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EndToEndIT {

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

    @Test
    void endToEnd_createOrderAndVerifyKafkaEvent() {
        // Create a consumer before posting the order
        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class,
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl(),
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true
        );

        try (KafkaConsumer<String, OrderCreated> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(TopicNames.ORDERS));

            // POST an order via REST API
            CreateOrderRequest request = new CreateOrderRequest(
                    "e2e-customer",
                    List.of(new OrderLineDto("SKU-E2E", 3), new OrderLineDto("SKU-E2E-2", 1)),
                    new BigDecimal("249.95"));

            ResponseEntity<Map> response = restTemplate.postForEntity("/orders", request, Map.class);

            // Verify HTTP response
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String orderId = (String) response.getBody().get("orderId");
            assertThat(orderId).isNotBlank();

            // Verify Kafka event
            ConsumerRecord<String, OrderCreated> record = pollForRecord(consumer);
            assertThat(record).isNotNull();
            assertThat(record.key()).isEqualTo(orderId);

            OrderCreated event = record.value();
            assertThat(event.getId()).isEqualTo(orderId);
            assertThat(event.getCustomerId()).isEqualTo("e2e-customer");
            assertThat(event.getTotal()).isEqualTo("249.95");
            assertThat(event.getStatus()).isEqualTo("CREATED");
            assertThat(event.getLines()).hasSize(2);
            assertThat(event.getLines().get(0).getSku()).isEqualTo("SKU-E2E");
            assertThat(event.getLines().get(0).getQty()).isEqualTo(3);
            assertThat(event.getLines().get(1).getSku()).isEqualTo("SKU-E2E-2");
            assertThat(event.getCreatedAt()).isNotBlank();
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
}
