package com.designpattern.cognitorbac.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.verifiedpermissions.VerifiedPermissionsClient;

/**
 * Wires the AWS Verified Permissions client for policy management and
 * runtime authorization decisions.
 */
@Configuration
@EnableConfigurationProperties(VerifiedPermissionsProperties.class)
public class VerifiedPermissionsConfig {

    @Bean
    public VerifiedPermissionsClient verifiedPermissionsClient(VerifiedPermissionsProperties properties) {
        return VerifiedPermissionsClient.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
