package com.designpattern.cognitorbac.dto.avp;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to evaluate an authorization decision against AVP.
 * Used for testing/debugging authorization outside of the interceptor flow.
 */
public record AuthorizationRequest(

        @NotBlank(message = "identityToken is required")
        String identityToken,

        @NotBlank(message = "action is required")
        String action,

        @NotBlank(message = "module is required")
        String module,

        @NotBlank(message = "resourceType is required")
        String resourceType
) {
}
