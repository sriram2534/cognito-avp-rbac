package com.designpattern.cognitorbac.audit;

/**
 * Enumeration of all auditable actions in the RBAC system.
 * New actions can be added here without changing the audit infrastructure.
 */
public enum AuditAction {

    GROUP_CREATED,
    GROUP_UPDATED,
    GROUP_DELETED,

    USER_ADDED_TO_GROUP,
    USER_REMOVED_FROM_GROUP,
    USER_METADATA_UPDATED,
    USER_ENABLED,
    USER_DISABLED,

    PERMISSION_CREATED,
    PERMISSION_DESCRIPTION_UPDATED,
    PERMISSION_ACTIVATED,
    PERMISSION_DEACTIVATED,

    ROLE_PERMISSION_GRANTED,
    ROLE_PERMISSION_REVOKED,
    ROLE_PERMISSION_RESTORED,

    POLICY_CREATED,
    POLICY_UPDATED,
    POLICY_DELETED
}
