package com.designpattern.cognitorbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddUsersToRoleRequest(
        @NotEmpty(message = "at least one userSub is required")
        @Size(max = 50, message = "no more than 50 users can be assigned in one request")
        List<@NotBlank String> userSubs
) {
}
