package com.designpattern.cognitorbac.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Delivery configuration for committed authorization-invalidation outbox events. */
@Getter
@Setter
@ConfigurationProperties(prefix = "authorization-events")
public class AuthorizationEventsProperties {
    /** Blank disables external delivery while retaining the MongoDB outbox. */
    private String topicArn;
    private String region = "us-east-1";
    private long publishIntervalMs = 1000;
}
