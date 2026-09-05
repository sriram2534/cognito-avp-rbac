package com.designpattern.cognitorbac.dto;

import com.designpattern.cognitorbac.role.NexusRoleStatus;

import java.time.Instant;

public record RoleResponse(
        String roleId,
        String roleKey,
        String module,
        String name,
        String displayName,
        String description,
        NexusRoleStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
