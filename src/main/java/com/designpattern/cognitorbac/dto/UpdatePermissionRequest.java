package com.designpattern.cognitorbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Permission coordinates are deliberately absent: they are immutable identity. */
public record UpdatePermissionRequest(@NotBlank @Size(max = 2048) String description) {
}
