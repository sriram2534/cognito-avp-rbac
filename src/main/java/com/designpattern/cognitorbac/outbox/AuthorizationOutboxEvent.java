package com.designpattern.cognitorbac.outbox;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Transactional outbox record. A separate publisher delivers committed records
 * to SNS; the source mutation never calls a remote broker in its transaction.
 */
@Document(collection = "authorization_outbox")
public class AuthorizationOutboxEvent {
    @Id
    private String id;
    @Indexed
    private String eventType;
    @Indexed
    private String aggregateId;
    private Map<String, Object> payload;
    private String correlationId;
    @Indexed
    private Instant occurredAt;
    @Indexed
    private Instant publishedAt;
    private int publishAttempts;
    private String lastError;
    @Version
    private Long version;

    protected AuthorizationOutboxEvent() {
    }

    public AuthorizationOutboxEvent(String eventType, String aggregateId, Map<String, Object> payload,
                                    String correlationId) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = Map.copyOf(payload);
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }

    public String getId() { return id; }
    public String getEventType() { return eventType; }
    public String getAggregateId() { return aggregateId; }
    public Map<String, Object> getPayload() { return payload; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getPublishAttempts() { return publishAttempts; }
    public String getLastError() { return lastError; }
    public void markPublished() { this.publishedAt = Instant.now(); this.lastError = null; }
    public void markFailed(String error) { this.publishAttempts++; this.lastError = error; }
}
