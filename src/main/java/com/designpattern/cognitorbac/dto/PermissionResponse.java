package com.designpattern.cognitorbac.dto;

import com.designpattern.cognitorbac.permission.PermissionStatus;

import java.time.Instant;

public record PermissionResponse(
        String permissionId,
        String module,
        String resourceType,
        String access,
        String displayKey,
        String description,
        PermissionStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
