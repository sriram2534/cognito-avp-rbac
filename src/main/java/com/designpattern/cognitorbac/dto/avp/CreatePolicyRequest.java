package com.designpattern.cognitorbac.dto.avp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to create a new Cedar policy in AVP.
 *
 * @param groupName    the Cognito group this policy grants access to (module:resource:access)
 * @param action       the Cedar action (Read, Write, Publish, Export, Approve, Admin)
 * @param module       the target resource module
 * @param resourceType the target resource type (use "global" for all resources in module)
 * @param description  optional human-readable description
 */
public record CreatePolicyRequest(

        @NotBlank(message = "groupName is required")
        @Pattern(regexp = "^[a-z_]+:[a-z_]+:[a-z_]+$",
                message = "groupName must follow pattern module:resource:access")
        String groupName,

        @NotBlank(message = "action is required")
        @Pattern(regexp = "^(Read|Write|Publish|Export|Approve|Admin)$",
                message = "action must be one of: Read, Write, Publish, Export, Approve, Admin")
        String action,

        @NotBlank(message = "module is required")
        String module,

        @NotBlank(message = "resourceType is required")
        String resourceType,

        String description
) {
}
