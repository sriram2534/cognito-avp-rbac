package com.designpattern.cognitorbac.exception;

/**
 * Thrown when the audit query layer encounters an unrecoverable error
 * (e.g. MongoDB unavailable during a read query).
 *
 * <p>This exception is used for audit query failures. Audit-write failures
 * propagate directly and roll back the associated authorization mutation.</p>
 */
public class AuditException extends RuntimeException {

    public AuditException(String message, Throwable cause) {
        super(message, cause);
    }
}
