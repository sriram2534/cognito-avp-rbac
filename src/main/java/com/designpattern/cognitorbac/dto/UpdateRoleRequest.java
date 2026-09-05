package com.designpattern.cognitorbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
        @NotBlank @Size(max = 128) String displayName,
        @Size(max = 2048) String description
) {
}
