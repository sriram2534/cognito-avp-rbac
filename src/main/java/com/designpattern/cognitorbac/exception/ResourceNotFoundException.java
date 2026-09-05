package com.designpattern.cognitorbac.exception;

/**
 * Thrown when a requested identity or authorization resource does not exist.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException user(String username) {
        return new ResourceNotFoundException("User not found: " + username);
    }
}
