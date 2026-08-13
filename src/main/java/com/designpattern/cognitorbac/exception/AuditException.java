package com.designpattern.cognitorbac.exception;

/**
 * Thrown when the audit query layer encounters an unrecoverable error
 * (e.g. MongoDB unavailable during a read query).
 *
 * <p>Audit <em>write</em> failures are never propagated — they are swallowed
 * in {@code AuditService} so business operations are never blocked.
 * This exception is only used for audit <em>read</em> (query) failures
 * where the caller explicitly requested audit data and must know it failed.</p>
 */
public class AuditException extends RuntimeException {

    public AuditException(String message, Throwable cause) {
        super(message, cause);
    }
}
