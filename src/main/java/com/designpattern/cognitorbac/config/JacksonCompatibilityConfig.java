package com.designpattern.cognitorbac.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 exposes a managed Jackson 3 mapper while the AWS SNS outbox
 * serializer uses Jackson 2. Keep this mapper isolated to that integration.
 */
@Configuration
public class JacksonCompatibilityConfig {
    @Bean
    ObjectMapper authorizationEventsObjectMapper() {
        return new ObjectMapper();
    }
}
