package com.designpattern.cognitorbac.messaging.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatcherTests {
    @Test
    void marksSuccessfullyPublishedEvent() {
        MongoOutboxStore store = mock(MongoOutboxStore.class);
        SqsMessagePublisher publisher = mock(SqsMessagePublisher.class);
        OutboxProperties properties = properties();
        OutboxEvent event = event();
        when(store.claimNext(any(Instant.class), any(Instant.class)))
                .thenReturn(Optional.of(event), Optional.empty());
        when(publisher.publish(event)).thenReturn("sqs-message-1");
        when(store.markPublished(eq(event), eq("sqs-message-1"), any(Instant.class))).thenReturn(true);

        new OutboxDispatcher(store, publisher, properties).dispatchAvailable();

        verify(store).markPublished(eq(event), eq("sqs-message-1"), any(Instant.class));
    }

    @Test
    void schedulesRetryWhenSqsPublishFails() {
        MongoOutboxStore store = mock(MongoOutboxStore.class);
        SqsMessagePublisher publisher = mock(SqsMessagePublisher.class);
        OutboxProperties properties = properties();
        OutboxEvent event = event();
        when(store.claimNext(any(Instant.class), any(Instant.class)))
                .thenReturn(Optional.of(event), Optional.empty());
        when(publisher.publish(event)).thenThrow(new IllegalStateException("SQS unavailable"));
        when(store.markForRetry(eq(event), any(Instant.class), any(String.class))).thenReturn(true);

        new OutboxDispatcher(store, publisher, properties).dispatchAvailable();

        verify(store).markForRetry(eq(event), any(Instant.class),
                eq("IllegalStateException: SQS unavailable"));
    }

    private OutboxProperties properties() {
        OutboxProperties properties = new OutboxProperties();
        properties.setBatchSize(2);
        properties.setLeaseDuration(Duration.ofSeconds(30));
        properties.setInitialRetryDelay(Duration.ofSeconds(2));
        properties.setMaxRetryDelay(Duration.ofMinutes(5));
        properties.setMaxAttempts(10);
        return properties;
    }

    private OutboxEvent event() {
        return OutboxEvent.pending(new OutboxMessage(
                        "nexus.authorization.changed", 1, "ROLE", "role-1", "request-1",
                        Map.of("changeType", "ROLE_DEACTIVATED")),
                "nexus-rbac-admin", Instant.parse("2026-09-06T10:00:00Z"));
    }
}
