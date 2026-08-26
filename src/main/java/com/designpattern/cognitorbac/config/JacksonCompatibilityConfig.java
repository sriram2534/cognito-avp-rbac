package com.designpattern.cognitorbac.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The legacy AVP schema code still uses Jackson 2 while Spring Boot 4 exposes
 * its managed Jackson 3 mapper. Keep a narrow Jackson 2 mapper for those
 * administrative payloads and the SNS outbox serializer.
 */
@Configuration
public class JacksonCompatibilityConfig {
    @Bean
    ObjectMapper legacyObjectMapper() {
        return new ObjectMapper();
    }
}
