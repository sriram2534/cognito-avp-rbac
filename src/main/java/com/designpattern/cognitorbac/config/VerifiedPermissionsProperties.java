package com.designpattern.cognitorbac.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed configuration for AWS Verified Permissions integration.
 * Bound from the {@code verified-permissions.*} namespace.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "verified-permissions")
public class VerifiedPermissionsProperties {

    /**
     * AWS region hosting the AVP Policy Store.
     */
    @NotBlank
    private String region;

    /**
     * The AVP Policy Store identifier. If blank, bootstrap will create one.
     */
    private String policyStoreId;

    /**
     * The Cognito User Pool ARN for identity source configuration.
     */
    private String userPoolArn;

    /**
     * Cedar schema namespace. All entity types and actions are prefixed with this.
     */
    @NotBlank
    private String namespace = "Portal";

    /**
     * The super admin group that has unrestricted access and can create module admins.
     */
    @NotBlank
    private String superAdminGroup = "global:global:admin";
}
