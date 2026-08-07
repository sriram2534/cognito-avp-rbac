package com.designpattern.cognitorbac.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * Wires the AWS Cognito Identity Provider client used for all admin-level
 * user/group operations against the configured User Pool.
 *
 * <p>Credentials are resolved via the {@link DefaultCredentialsProvider} chain
 * (environment variables, system properties, profile files, container/EC2/IRSA
 * roles). No secrets are ever hard-coded.</p>
 */
@Configuration
@EnableConfigurationProperties(CognitoProperties.class)
public class CognitoConfig {

    @Bean
    public CognitoIdentityProviderClient cognitoIdentityProviderClient(CognitoProperties properties) {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
