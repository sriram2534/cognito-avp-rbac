package com.designpattern.cognitorbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank @Size(max = 127) String roleKey,
        @NotBlank @Size(max = 128) String displayName,
        @Size(max = 2048) String description
) {
}
