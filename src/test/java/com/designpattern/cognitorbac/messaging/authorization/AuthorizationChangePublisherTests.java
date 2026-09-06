package com.designpattern.cognitorbac.messaging.authorization;

import com.designpattern.cognitorbac.audit.AuditAction;
import com.designpattern.cognitorbac.audit.AuditContext;
import com.designpattern.cognitorbac.messaging.outbox.OutboxMessage;
import com.designpattern.cognitorbac.messaging.outbox.TransactionalOutbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthorizationChangePublisherTests {
    @AfterEach
    void clearContext() {
        AuditContext.clear();
    }

    @Test
    void userRoleRemovalTargetsOnlyTheAffectedUserSession() {
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        AuditContext.set("admin-sub", "admin@nexus.example", "remove access", "request-1");
        AuthorizationChangePublisher publisher = new AuthorizationChangePublisher(outbox);

        publisher.enqueue(AuditAction.USER_ROLE_REMOVED, "USER_ROLE", "user-1:role-1",
                "role-1", "billing:admin", null, "user-1");

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).enqueue(captor.capture());
        OutboxMessage message = captor.getValue();
        assertEquals(AuthorizationChangePublisher.EVENT_TYPE, message.eventType());
        assertEquals("request-1", message.correlationId());
        assertEquals("USER", message.payload().get("impactScope"));
        assertEquals("FORCE_REFRESH_OR_LOGOUT", message.payload().get("sessionDirective"));
        assertEquals("user-1", message.payload().get("targetUserSub"));
        assertEquals("SESSION_INVALIDATION_SIGNAL", message.payload().get("deliverySemantics"));
    }

    @Test
    void permissionCreationDoesNotPublishBecauseItCannotAffectAnExistingSession() {
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        AuthorizationChangePublisher publisher = new AuthorizationChangePublisher(outbox);

        publisher.enqueue(AuditAction.PERMISSION_CREATED, "PERMISSION", "permission-1",
                null, null, "permission-1", null);

        verifyNoInteractions(outbox);
    }

    @ParameterizedTest
    @CsvSource({
            "ROLE_ACTIVATED, ROLE",
            "ROLE_DEACTIVATED, ROLE",
            "USER_ROLE_ASSIGNED, USER",
            "USER_ROLE_REMOVED, USER",
            "USER_ROLE_RESTORED, USER",
            "PERMISSION_ACTIVATED, PERMISSION",
            "PERMISSION_DEACTIVATED, PERMISSION",
            "ROLE_PERMISSION_GRANTED, ROLE",
            "ROLE_PERMISSION_REVOKED, ROLE",
            "ROLE_PERMISSION_RESTORED, ROLE"
    })
    void mapsEverySessionAffectingActionToItsImpactScope(AuditAction action, String expectedScope) {
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        AuthorizationChangePublisher publisher = new AuthorizationChangePublisher(outbox);

        publisher.enqueue(action, "AGGREGATE", "aggregate-1",
                "role-1", "billing:admin", "permission-1", "user-1");

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).enqueue(captor.capture());
        assertEquals(expectedScope, captor.getValue().payload().get("impactScope"));
        assertEquals("FORCE_REFRESH_OR_LOGOUT", captor.getValue().payload().get("sessionDirective"));
    }
}
