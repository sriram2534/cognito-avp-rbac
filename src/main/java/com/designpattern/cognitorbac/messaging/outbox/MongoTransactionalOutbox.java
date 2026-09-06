package com.designpattern.cognitorbac.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Writes outbox events in the caller's existing MongoDB transaction. */
@Service
public class MongoTransactionalOutbox implements TransactionalOutbox {
    private static final Logger log = LoggerFactory.getLogger(MongoTransactionalOutbox.class);
    private final MongoOutboxStore store;
    private final OutboxProperties properties;

    public MongoTransactionalOutbox(MongoOutboxStore store, OutboxProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(OutboxMessage message) {
        if (!properties.isEnabled()) {
            log.atDebug().addKeyValue("event", "outbox_disabled")
                    .addKeyValue("eventType", message.eventType())
                    .log("Transactional outbox is disabled");
            return;
        }
        OutboxEvent event = store.insert(OutboxEvent.pending(message, properties.getSource(), Instant.now()));
        log.atDebug().addKeyValue("event", "outbox_event_enqueued")
                .addKeyValue("outboxEventId", event.getEventId())
                .addKeyValue("eventType", event.getEventType())
                .addKeyValue("aggregateType", event.getAggregateType())
                .log("Outbox event enqueued");
    }
}
