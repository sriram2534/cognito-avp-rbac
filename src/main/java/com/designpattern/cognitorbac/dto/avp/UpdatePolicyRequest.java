package com.designpattern.cognitorbac.dto.avp;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to update an existing Cedar policy statement in AVP.
 */
public record UpdatePolicyRequest(

        @NotBlank(message = "policyId is required")
        String policyId,

        @NotBlank(message = "statement is required")
        String statement,

        String description
) {
}
