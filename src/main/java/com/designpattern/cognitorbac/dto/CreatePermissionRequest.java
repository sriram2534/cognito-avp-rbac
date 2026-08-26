package com.designpattern.cognitorbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank @Size(max = 64) String module,
        @NotBlank @Size(max = 64) String resourceType,
        @NotBlank @Size(max = 32) String access,
        @Size(max = 2048) String description
) {
}
