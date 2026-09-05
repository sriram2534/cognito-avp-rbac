package com.designpattern.cognitorbac.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a successful authorization-source mutation for internal audit capture.
 *
 * <p>The aspect derives the concrete audit action from the operation and the
 * before/after state. This keeps mutation services free of audit persistence
 * concerns while still avoiding audit records for idempotent requests.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthorizationAudit {

    AuthorizationAuditOperation operation();
}
