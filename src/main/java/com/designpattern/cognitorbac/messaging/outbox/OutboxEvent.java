package com.designpattern.cognitorbac.messaging.outbox;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Durable provider-neutral event. MongoDB {@code _id} never leaves this service. */
@Document(collection = "outbox_events")
@CompoundIndex(name = "ix_outbox_dispatch", def = "{'status': 1, 'nextAttemptAt': 1, 'createdAt': 1}")
@CompoundIndex(name = "ix_outbox_lease_recovery", def = "{'status': 1, 'leaseUntil': 1, 'createdAt': 1}")
public class OutboxEvent {
    @Id
    private String id;
    @Indexed(name = "ux_outbox_event_id", unique = true)
    private String eventId;
    private String eventType;
    private int schemaVersion;
    private String source;
    private String aggregateType;
    private String aggregateId;
    private String correlationId;
    private Map<String, Object> payload;
    private OutboxStatus status;
    private int attempts;
    private Instant createdAt;
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    private String leaseToken;
    @Indexed(name = "ix_outbox_published_ttl", expireAfter = "7d")
    private Instant publishedAt;
    private String providerMessageId;
    private String lastError;
    protected OutboxEvent() {
    }

    private OutboxEvent(OutboxMessage message, String source, Instant now) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = message.eventType();
        this.schemaVersion = message.schemaVersion();
        this.source = source;
        this.aggregateType = message.aggregateType();
        this.aggregateId = message.aggregateId();
        this.correlationId = message.correlationId();
        this.payload = message.payload();
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    static OutboxEvent pending(OutboxMessage message, String source, Instant now) {
        return new OutboxEvent(message, source, now);
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getSource() { return source; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getCorrelationId() { return correlationId; }
    public Map<String, Object> getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public String getLeaseToken() { return leaseToken; }
}
