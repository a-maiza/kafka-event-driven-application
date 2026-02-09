package com.example.streams.controller;

import com.example.streams.topology.StatusCountsTopology;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Iterator;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class KpiControllerTest {

    @Mock
    private StreamsBuilderFactoryBean factoryBean;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        KpiController controller = new KpiController(factoryBean);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getStatusCounts_streamsRunning_shouldReturn200WithCounts() throws Exception {
        KafkaStreams streams = mock(KafkaStreams.class);
        when(streams.state()).thenReturn(KafkaStreams.State.RUNNING);

        ReadOnlyKeyValueStore<String, Long> store = mock(ReadOnlyKeyValueStore.class);
        when(streams.store(any(StoreQueryParameters.class))).thenReturn(store);

        List<KeyValue<String, Long>> entries = List.of(
                new KeyValue<>("CONFIRMED", 5L),
                new KeyValue<>("REJECTED", 2L));
        KeyValueIterator<String, Long> iterator = new SimpleKeyValueIterator<>(entries.iterator());
        when(store.all()).thenReturn(iterator);

        when(factoryBean.getKafkaStreams()).thenReturn(streams);

        mockMvc.perform(get("/kpis/status-counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CONFIRMED").value(5))
                .andExpect(jsonPath("$.REJECTED").value(2));
    }

    @Test
    void getStatusCounts_streamsNull_shouldReturn503() throws Exception {
        when(factoryBean.getKafkaStreams()).thenReturn(null);

        mockMvc.perform(get("/kpis/status-counts"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getStatusCounts_streamsNotRunning_shouldReturn503() throws Exception {
        KafkaStreams streams = mock(KafkaStreams.class);
        when(streams.state()).thenReturn(KafkaStreams.State.REBALANCING);
        when(factoryBean.getKafkaStreams()).thenReturn(streams);

        mockMvc.perform(get("/kpis/status-counts"))
                .andExpect(status().isServiceUnavailable());
    }

    private static class SimpleKeyValueIterator<K, V> implements KeyValueIterator<K, V> {
        private final Iterator<KeyValue<K, V>> delegate;

        SimpleKeyValueIterator(Iterator<KeyValue<K, V>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public KeyValue<K, V> next() {
            return delegate.next();
        }

        @Override
        public void close() {
        }

        @Override
        public K peekNextKey() {
            throw new UnsupportedOperationException();
        }
    }
}
