package com.designpattern.cognitorbac.dto.avp;

import java.time.Instant;

/**
 * Response representing a Cedar policy stored in AVP.
 */
public record PolicyResponse(
        String policyId,
        String policyType,
        String statement,
        String description,
        Instant createdDate,
        Instant lastUpdatedDate
) {
}
