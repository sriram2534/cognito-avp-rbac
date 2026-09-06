package com.designpattern.cognitorbac.messaging.outbox;

public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    PUBLISHED,
    FAILED
}
