package com.designpattern.cognitorbac.exception;

/**
 * Thrown when a requested Cognito resource (user or group) does not exist.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException user(String username) {
        return new ResourceNotFoundException("User not found: " + username);
    }

    public static ResourceNotFoundException group(String groupName) {
        return new ResourceNotFoundException("Group not found: " + groupName);
    }
}
