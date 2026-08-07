package com.designpattern.cognitorbac.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating a Cognito group. All fields are optional; only the
 * non-null fields are applied.
 */
public record UpdateGroupRequest(
        @Size(max = 2048)
        String description,

        @Min(value = 0, message = "precedence must be zero or positive")
        Integer precedence,

        String roleArn
) {
}
