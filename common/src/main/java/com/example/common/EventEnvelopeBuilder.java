package com.example.common;

import com.example.common.avro.EventEnvelope;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;

public final class EventEnvelopeBuilder {

    private EventEnvelopeBuilder() {
    }

    public static EventEnvelope wrap(SpecificRecordBase payload, String producer, String correlationId)
            throws IOException {
        return EventEnvelope.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setType(payload.getSchema().getFullName())
                .setVersion(1)
                .setOccurredAt(Instant.now().toString())
                .setProducer(producer)
                .setCorrelationId(correlationId)
                .setPayload(serializePayload(payload))
                .build();
    }

    public static EventEnvelope wrap(SpecificRecordBase payload, String producer,
                                     String correlationId, int version) throws IOException {
        return EventEnvelope.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setType(payload.getSchema().getFullName())
                .setVersion(version)
                .setOccurredAt(Instant.now().toString())
                .setProducer(producer)
                .setCorrelationId(correlationId)
                .setPayload(serializePayload(payload))
                .build();
    }

    private static ByteBuffer serializePayload(SpecificRecordBase record) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DatumWriter<SpecificRecordBase> writer = new SpecificDatumWriter<>(record.getSchema());
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
        writer.write(record, encoder);
        encoder.flush();
        return ByteBuffer.wrap(outputStream.toByteArray());
    }
}
