package com.designpattern.cognitorbac.exception;

/**
 * Wraps unexpected failures returned by the AWS Cognito service so that callers
 * receive a consistent 502-style error rather than a raw SDK exception.
 */
public class CognitoIntegrationException extends RuntimeException {

    public CognitoIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
