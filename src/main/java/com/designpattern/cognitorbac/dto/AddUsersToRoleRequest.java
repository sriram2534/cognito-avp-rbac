package com.designpattern.cognitorbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddUsersToRoleRequest(
        @NotEmpty(message = "at least one userSub is required")
        List<@NotBlank String> userSubs
) {
}
