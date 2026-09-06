package com.designpattern.cognitorbac.messaging.outbox;

/** Reusable boundary for atomically recording an integration event with domain data. */
public interface TransactionalOutbox {
    void enqueue(OutboxMessage message);
}
