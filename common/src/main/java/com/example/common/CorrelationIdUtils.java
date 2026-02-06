package com.example.common;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class CorrelationIdUtils {

    private CorrelationIdUtils() {
    }

    public static final String HEADER_NAME = "correlationId";
    public static final String MDC_KEY = "correlationId";

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static String getFromHeaders(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(HEADER_NAME);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    public static void setToHeaders(ProducerRecord<?, ?> record, String correlationId) {
        if (correlationId != null) {
            record.headers().add(HEADER_NAME, correlationId.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void setInMdc(String correlationId) {
        if (correlationId != null) {
            MDC.put(MDC_KEY, correlationId);
        }
    }

    public static String getFromMdc() {
        return MDC.get(MDC_KEY);
    }

    public static void clearMdc() {
        MDC.remove(MDC_KEY);
    }
}
