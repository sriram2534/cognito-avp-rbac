package com.designpattern.cognitorbac.audit;

/** Internal mutation categories understood by {@link AuthorizationAuditAspect}. */
public enum AuthorizationAuditOperation {
    ROLE_CREATED,
    ROLE_ACTIVATED,
    ROLE_DEACTIVATED,
    USER_ROLE_ASSOCIATED,
    USER_ROLE_REMOVED,
    PERMISSION_CREATED,
    PERMISSION_ACTIVATED,
    PERMISSION_DEACTIVATED,
    ROLE_PERMISSION_ASSOCIATED,
    ROLE_PERMISSION_REVOKED
}
