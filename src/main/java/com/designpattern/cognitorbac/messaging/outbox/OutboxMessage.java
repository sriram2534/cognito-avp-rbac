package com.designpattern.cognitorbac.messaging.outbox;

import java.util.Map;

/** Provider-neutral command persisted by the transactional outbox. */
public record OutboxMessage(
        String eventType,
        int schemaVersion,
        String aggregateType,
        String aggregateId,
        String correlationId,
        Map<String, Object> payload
) {
    public OutboxMessage {
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        if (aggregateType == null || aggregateType.isBlank()) throw new IllegalArgumentException("aggregateType is required");
        if (aggregateId == null || aggregateId.isBlank()) throw new IllegalArgumentException("aggregateId is required");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
