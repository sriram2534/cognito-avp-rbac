package com.designpattern.cognitorbac.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for adding one or more users to a group in a single call.
 */
public record AddUsersToGroupRequest(
        @NotEmpty(message = "at least one username is required")
        List<String> usernames
) {
}
