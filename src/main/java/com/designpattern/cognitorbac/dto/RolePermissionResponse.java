package com.designpattern.cognitorbac.dto;

import com.designpattern.cognitorbac.permission.RolePermissionStatus;

import java.time.Instant;

public record RolePermissionResponse(
        String roleKey,
        String permissionId,
        RolePermissionStatus status,
        Instant validFrom,
        Instant validUntil,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
