package com.designpattern.cognitorbac.dto;

import com.designpattern.cognitorbac.role.NexusUserRoleStatus;

import java.time.Instant;

public record UserRoleResponse(
        String userSub,
        String roleId,
        NexusUserRoleStatus status,
        Instant assignedAt,
        Instant removedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
