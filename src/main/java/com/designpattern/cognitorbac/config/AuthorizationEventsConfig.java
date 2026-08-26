package com.designpattern.cognitorbac.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
@EnableConfigurationProperties(AuthorizationEventsProperties.class)
public class AuthorizationEventsConfig {
    /**
     * Makes permission/relationship, audit, and outbox writes one MongoDB
     * transaction. Production MongoDB must be a replica set or sharded cluster.
     */
    @Bean
    PlatformTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean
    @ConditionalOnProperty(prefix = "authorization-events", name = "topic-arn")
    SnsClient authorizationEventsSnsClient(AuthorizationEventsProperties properties) {
        return SnsClient.builder().region(Region.of(properties.getRegion())).build();
    }
}
