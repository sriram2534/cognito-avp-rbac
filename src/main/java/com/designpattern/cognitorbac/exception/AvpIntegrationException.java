package com.designpattern.cognitorbac.exception;

/**
 * Wraps unexpected failures returned by AWS Verified Permissions so that callers
 * receive a consistent 502-style error rather than a raw SDK exception.
 */
public class AvpIntegrationException extends RuntimeException {

    public AvpIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static AvpIntegrationException forOperation(String operation, Throwable cause) {
        return new AvpIntegrationException("AVP operation failed: " + operation, cause);
    }
}
