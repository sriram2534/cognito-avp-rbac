package com.designpattern.cognitorbac.messaging.authorization;

import com.designpattern.cognitorbac.audit.AuditAction;
import com.designpattern.cognitorbac.audit.AuditContext;
import com.designpattern.cognitorbac.messaging.outbox.OutboxMessage;
import com.designpattern.cognitorbac.messaging.outbox.TransactionalOutbox;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps RBAC mutations to auth-service invalidation messages. */
@Component
public class AuthorizationChangePublisher {
    public static final String EVENT_TYPE = "nexus.authorization.changed";
    public static final int SCHEMA_VERSION = 1;
    private static final String SESSION_INVALIDATION_SIGNAL = "SESSION_INVALIDATION_SIGNAL";
    private static final String FORCE_REFRESH_OR_LOGOUT = "FORCE_REFRESH_OR_LOGOUT";

    private final TransactionalOutbox outbox;

    public AuthorizationChangePublisher(TransactionalOutbox outbox) {
        this.outbox = outbox;
    }

    public void enqueue(AuditAction action, String aggregateType, String aggregateId,
                        String roleId, String roleKey, String permissionId, String targetUserSub) {
        Impact impact = impact(action);
        if (!impact.invalidateSessions()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("changeType", action.name());
        payload.put("deliverySemantics", SESSION_INVALIDATION_SIGNAL);
        payload.put("impactScope", impact.scope());
        payload.put("sessionDirective", FORCE_REFRESH_OR_LOGOUT);
        putIfPresent(payload, "roleId", roleId);
        putIfPresent(payload, "roleKey", roleKey);
        putIfPresent(payload, "permissionId", permissionId);
        putIfPresent(payload, "targetUserSub", targetUserSub);

        AuditContext context = AuditContext.current();
        String correlationId = context == null ? null : context.getCorrelationId();
        outbox.enqueue(new OutboxMessage(EVENT_TYPE, SCHEMA_VERSION, aggregateType, aggregateId,
                correlationId, payload));
    }

    private Impact impact(AuditAction action) {
        return switch (action) {
            case USER_ROLE_ASSIGNED, USER_ROLE_REMOVED, USER_ROLE_RESTORED -> new Impact("USER", true);
            case ROLE_ACTIVATED, ROLE_DEACTIVATED,
                    ROLE_PERMISSION_GRANTED, ROLE_PERMISSION_REVOKED, ROLE_PERMISSION_RESTORED ->
                    new Impact("ROLE", true);
            case PERMISSION_ACTIVATED, PERMISSION_DEACTIVATED -> new Impact("PERMISSION", true);
            case ROLE_CREATED, PERMISSION_CREATED, ROLE_UPDATED, PERMISSION_DESCRIPTION_UPDATED ->
                    new Impact("NONE", false);
        };
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }

    private record Impact(String scope, boolean invalidateSessions) {
    }
}
