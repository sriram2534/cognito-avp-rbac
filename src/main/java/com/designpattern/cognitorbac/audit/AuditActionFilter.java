package com.designpattern.cognitorbac.audit;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** Defines the authorization-source mutations that produce durable audit records. */
@Component
public class AuditActionFilter {

    private static final Set<AuditAction> CAPTURED_ACTIONS = EnumSet.of(
            AuditAction.ROLE_CREATED,
            AuditAction.ROLE_ACTIVATED,
            AuditAction.ROLE_DEACTIVATED,
            AuditAction.USER_ROLE_ASSIGNED,
            AuditAction.USER_ROLE_REMOVED,
            AuditAction.USER_ROLE_RESTORED,
            AuditAction.PERMISSION_CREATED,
            AuditAction.PERMISSION_ACTIVATED,
            AuditAction.PERMISSION_DEACTIVATED,
            AuditAction.ROLE_PERMISSION_GRANTED,
            AuditAction.ROLE_PERMISSION_REVOKED,
            AuditAction.ROLE_PERMISSION_RESTORED
    );

    public boolean shouldCapture(AuditAction action) {
        return action != null && CAPTURED_ACTIONS.contains(action);
    }
}
