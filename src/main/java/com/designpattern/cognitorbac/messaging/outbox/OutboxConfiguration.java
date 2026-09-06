package com.designpattern.cognitorbac.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "messaging.outbox", name = "enabled", havingValue = "true")
    SqsClient outboxSqsClient(OutboxProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(properties.getPublishTimeout())
                        .apiCallAttemptTimeout(properties.getPublishAttemptTimeout())
                        .build())
                .build();
    }
}
