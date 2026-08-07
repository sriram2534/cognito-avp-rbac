package com.designpattern.cognitorbac.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a Cognito group.
 */
public record CreateGroupRequest(
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "[\\p{L}\\p{M}\\p{S}\\p{N}\\p{P}]+",
                message = "groupName contains invalid characters")
        String groupName,

        @Size(max = 2048)
        String description,

        @Min(value = 0, message = "precedence must be zero or positive")
        Integer precedence,

        String roleArn
) {
}
