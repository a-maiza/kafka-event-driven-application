package com.example.streams.controller;

import com.example.streams.topology.StatusCountsTopology;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/kpis")
public class KpiController {

    private final StreamsBuilderFactoryBean factoryBean;

    public KpiController(StreamsBuilderFactoryBean factoryBean) {
        this.factoryBean = factoryBean;
    }

    @GetMapping("/status-counts")
    public ResponseEntity<Map<String, Long>> getStatusCounts() {
        KafkaStreams streams = factoryBean.getKafkaStreams();
        if (streams == null || streams.state() != KafkaStreams.State.RUNNING) {
            return ResponseEntity.status(503).build();
        }

        ReadOnlyKeyValueStore<String, Long> store = streams.store(
                StoreQueryParameters.fromNameAndType(
                        StatusCountsTopology.STATUS_COUNTS_STORE,
                        QueryableStoreTypes.keyValueStore()));

        Map<String, Long> counts = new LinkedHashMap<>();
        try (KeyValueIterator<String, Long> iterator = store.all()) {
            while (iterator.hasNext()) {
                KeyValue<String, Long> entry = iterator.next();
                counts.put(entry.key, entry.value);
            }
        }
        return ResponseEntity.ok(counts);
    }
}
