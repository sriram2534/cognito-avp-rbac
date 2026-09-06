package com.designpattern.cognitorbac.messaging.outbox;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MongoTransactionalOutboxTests {
    @Test
    void persistsProviderNeutralEventWhenEnabled() {
        MongoOutboxStore store = mock(MongoOutboxStore.class);
        OutboxProperties properties = properties(true);
        MongoTransactionalOutbox outbox = new MongoTransactionalOutbox(store, properties);
        OutboxMessage message = message();
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        when(store.insert(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        outbox.enqueue(message);

        verify(store).insert(captor.getValue());
        assertEquals("nexus-rbac-admin", captor.getValue().getSource());
        assertEquals(OutboxStatus.PENDING, captor.getValue().getStatus());
        assertEquals("nexus.authorization.changed", captor.getValue().getEventType());
    }

    @Test
    void localDisabledModeDoesNotWriteOutboxEvents() {
        MongoOutboxStore store = mock(MongoOutboxStore.class);
        MongoTransactionalOutbox outbox = new MongoTransactionalOutbox(store, properties(false));

        outbox.enqueue(message());

        verifyNoInteractions(store);
    }

    private OutboxProperties properties(boolean enabled) {
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(enabled);
        properties.setSource("nexus-rbac-admin");
        return properties;
    }

    private OutboxMessage message() {
        return new OutboxMessage("nexus.authorization.changed", 1, "ROLE", "role-1",
                "request-1", Map.of("changeType", "ROLE_DEACTIVATED"));
    }
}
