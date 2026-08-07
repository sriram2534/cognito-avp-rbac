package com.designpattern.cognitorbac.dto.avp;

import java.util.List;

/**
 * Response from an AVP authorization evaluation.
 */
public record AuthorizationResponse(
        String decision,
        List<String> determiningPolicies,
        List<String> errors
) {
}
