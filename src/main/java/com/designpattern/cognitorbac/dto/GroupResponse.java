package com.designpattern.cognitorbac.dto;

import java.time.Instant;

/**
 * Public representation of a Cognito group.
 */
public record GroupResponse(
        String groupName,
        String description,
        Integer precedence,
        String roleArn,
        Instant createdAt,
        Instant lastModifiedAt
) {
}
