package com.example.streams.topology;

import com.example.common.TopicNames;
import com.example.common.avro.OrderStatusChanged;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StatusCountsTopology {

    public static final String STATUS_COUNTS_STORE = "status-counts-store";

    @Value("${spring.kafka.streams.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Autowired
    void buildPipeline(StreamsBuilder builder) {
        SpecificAvroSerde<OrderStatusChanged> valueSerde = new SpecificAvroSerde<>();
        valueSerde.configure(Map.of("schema.registry.url", schemaRegistryUrl), false);

        builder.<String, OrderStatusChanged>stream(TopicNames.ORDER_STATUS,
                        Consumed.with(Serdes.String(), valueSerde))
                .groupBy((key, value) -> value.getFinalStatus(),
                        Grouped.with(Serdes.String(), valueSerde))
                .count(Materialized.as(STATUS_COUNTS_STORE));
    }
}
