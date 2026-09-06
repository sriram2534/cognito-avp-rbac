package com.designpattern.cognitorbac.messaging.outbox;

import java.time.Instant;
import java.util.Map;

/** Stable provider-neutral message body delivered to downstream services. */
public record OutboxEnvelope(
        String eventId,
        String eventType,
        int schemaVersion,
        String source,
        Instant occurredAt,
        String correlationId,
        String aggregateType,
        String aggregateId,
        Map<String, Object> payload
) {
    static OutboxEnvelope from(OutboxEvent event) {
        return new OutboxEnvelope(event.getEventId(), event.getEventType(), event.getSchemaVersion(),
                event.getSource(), event.getCreatedAt(), event.getCorrelationId(), event.getAggregateType(),
                event.getAggregateId(), event.getPayload());
    }
}
