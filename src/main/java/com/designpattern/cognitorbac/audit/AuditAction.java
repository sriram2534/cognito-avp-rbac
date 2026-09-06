package com.designpattern.cognitorbac.audit;

/**
 * Enumeration of all auditable actions in the RBAC system.
 * New actions can be added here without changing the audit infrastructure.
 */
public enum AuditAction {
    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_ACTIVATED,
    ROLE_DEACTIVATED,
    USER_ROLE_ASSIGNED,
    USER_ROLE_REMOVED,
    USER_ROLE_RESTORED,

    PERMISSION_CREATED,
    PERMISSION_DESCRIPTION_UPDATED,
    PERMISSION_ACTIVATED,
    PERMISSION_DEACTIVATED,

    ROLE_PERMISSION_GRANTED,
    ROLE_PERMISSION_REVOKED,
    ROLE_PERMISSION_RESTORED
}
