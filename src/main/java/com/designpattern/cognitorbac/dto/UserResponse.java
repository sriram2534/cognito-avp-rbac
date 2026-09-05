package com.designpattern.cognitorbac.dto;

import java.time.Instant;
import java.util.List;

/**
 * Public representation of a Cognito user, enriched with Nexus role memberships.
 */
public record UserResponse(
        String username,
        String sub,
        String email,
        boolean emailVerified,
        String name,
        String givenName,
        String familyName,
        boolean enabled,
        String status,
        List<String> roles,
        Instant createdAt,
        Instant lastModifiedAt
) {
}
