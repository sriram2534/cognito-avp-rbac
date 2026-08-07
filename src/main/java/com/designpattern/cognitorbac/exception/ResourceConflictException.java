package com.designpattern.cognitorbac.exception;

/**
 * Thrown when attempting to create a resource that already exists.
 */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
