package com.example.kafka.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageStreamProcessor {

    private static final String INPUT_TOPIC = "input-topic";
    private static final String OUTPUT_TOPIC = "output-topic";

    @Autowired
    public void buildPipeline(StreamsBuilder streamsBuilder) {
        KStream<String, String> inputStream = streamsBuilder.stream(
                INPUT_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> processedStream = inputStream
                .filter((key, value) -> value != null && !value.isEmpty())
                .mapValues(value -> {
                    log.info("Processing message: {}", value);
                    return value.toUpperCase();
                });

        processedStream.to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
    }

}
