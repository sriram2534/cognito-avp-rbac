package com.designpattern.cognitorbac.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Asynchronously claims committed events and delivers them to SQS with bounded backoff. */
@Component
@ConditionalOnProperty(prefix = "messaging.outbox", name = "enabled", havingValue = "true")
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int MAX_ERROR_LENGTH = 1_000;
    private final MongoOutboxStore store;
    private final SqsMessagePublisher publisher;
    private final OutboxProperties properties;

    public OutboxDispatcher(MongoOutboxStore store, SqsMessagePublisher publisher, OutboxProperties properties) {
        this.store = store;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${messaging.outbox.poll-interval:1s}")
    public void dispatchAvailable() {
        try {
            for (int sent = 0; sent < properties.getBatchSize(); sent++) {
                Instant now = Instant.now();
                var claimed = store.claimNext(now, now.plus(properties.getLeaseDuration()));
                if (claimed.isEmpty()) {
                    return;
                }
                dispatch(claimed.get());
            }
        } catch (RuntimeException exception) {
            log.atError().addKeyValue("event", "outbox_dispatch_cycle_failed")
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .setCause(exception)
                    .log("Outbox dispatch cycle failed");
        }
    }

    private void dispatch(OutboxEvent event) {
        try {
            String messageId = publisher.publish(event);
            if (!store.markPublished(event, messageId, Instant.now())) {
                log.atWarn().addKeyValue("event", "outbox_publish_lease_lost")
                        .addKeyValue("outboxEventId", event.getEventId())
                        .log("Outbox publish completed after its lease was lost");
                return;
            }
            log.atInfo().addKeyValue("event", "outbox_event_published")
                    .addKeyValue("outboxEventId", event.getEventId())
                    .addKeyValue("eventType", event.getEventType())
                    .addKeyValue("providerMessageId", messageId)
                    .addKeyValue("attempt", event.getAttempts())
                    .log("Outbox event published");
        } catch (RuntimeException exception) {
            failOrRetry(event, exception);
        }
    }

    private void failOrRetry(OutboxEvent event, RuntimeException exception) {
        String error = safeError(exception);
        if (event.getAttempts() >= properties.getMaxAttempts()) {
            if (!store.markFailed(event, error)) {
                logLeaseLost(event, "mark failed");
                return;
            }
            log.atError().addKeyValue("event", "outbox_event_failed")
                    .addKeyValue("outboxEventId", event.getEventId())
                    .addKeyValue("eventType", event.getEventType())
                    .addKeyValue("attempt", event.getAttempts())
                    .addKeyValue("failure", error)
                    .setCause(exception)
                    .log("Outbox event exhausted delivery attempts");
            return;
        }
        Duration delay = retryDelay(event.getAttempts());
        if (!store.markForRetry(event, Instant.now().plus(delay), error)) {
            logLeaseLost(event, "schedule retry");
            return;
        }
        log.atWarn().addKeyValue("event", "outbox_event_retry_scheduled")
                .addKeyValue("outboxEventId", event.getEventId())
                .addKeyValue("eventType", event.getEventType())
                .addKeyValue("attempt", event.getAttempts())
                .addKeyValue("retryDelayMs", delay.toMillis())
                .addKeyValue("failure", error)
                .log("Outbox event delivery will be retried");
    }

    private void logLeaseLost(OutboxEvent event, String operation) {
        log.atWarn().addKeyValue("event", "outbox_publish_lease_lost")
                .addKeyValue("outboxEventId", event.getEventId())
                .addKeyValue("attemptedOperation", operation)
                .log("Outbox state could not be updated after its lease was lost");
    }

    private Duration retryDelay(int attempts) {
        long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        long initialMillis = properties.getInitialRetryDelay().toMillis();
        long maximumMillis = properties.getMaxRetryDelay().toMillis();
        long delayMillis = initialMillis > maximumMillis / multiplier
                ? maximumMillis : initialMillis * multiplier;
        return Duration.ofMillis(Math.min(delayMillis, maximumMillis));
    }

    private String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        String value = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
