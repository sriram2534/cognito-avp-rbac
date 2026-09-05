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
     * Required so tokens issued for another application client are rejected.
     */
    @NotBlank
    private String appClientId;

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
