package com.designpattern.cognitorbac.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed configuration for the AWS Cognito integration.
 * Bound from the {@code cognito.*} namespace in application configuration.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "cognito")
public class CognitoProperties {

    /**
     * AWS region hosting the Cognito User Pool (e.g. {@code us-east-1}).
     */
    @NotBlank
    private String region;

    /**
     * The Cognito User Pool identifier (e.g. {@code us-east-1_abc123}).
     */
    @NotBlank
    private String userPoolId;

    /**
     * The Cognito App Client ID used as the expected JWT audience.
     * Optional: when blank, audience validation is skipped.
     */
    private String appClientId;

    /**
     * The Azure-managed Cognito group that is the source of truth for the
     * application's user base. The {@code /api/v1/users} endpoint lists only the
     * members of this group. Users are provisioned into it when they federate in
     * through Azure AD (SAML/OIDC).
     */
    @NotBlank
    private String sourceGroup;

    /**
     * The Cognito group that grants administrative privileges over the RBAC
     * write operations (create/update group, add/remove users). Users must be a
     * member of this group in Cognito to invoke protected write endpoints.
     */
    @NotBlank
    private String adminGroup = "rbac-admins";

    /**
     * Default page size used when the caller does not supply a limit.
     */
    private int defaultPageSize = 25;

    /**
     * Base issuer URI is derived from region + user pool id, but can be
     * overridden explicitly (useful for testing / custom domains).
     */
    private String issuerUri;

    public String resolveIssuerUri() {
        if (issuerUri != null && !issuerUri.isBlank()) {
            return issuerUri;
        }
        return "https://cognito-idp.%s.amazonaws.com/%s".formatted(region, userPoolId);
    }

    public String jwkSetUri() {
        return resolveIssuerUri() + "/.well-known/jwks.json";
    }
}
