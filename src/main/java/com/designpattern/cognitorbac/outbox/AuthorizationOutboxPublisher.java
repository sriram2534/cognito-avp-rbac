package com.designpattern.cognitorbac.outbox;

import com.designpattern.cognitorbac.config.AuthorizationEventsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Delivers committed outbox records to SNS. Delivery is intentionally at-least
 * once; receivers must de-duplicate by {@code eventId}.
 */
@Component
@ConditionalOnBean(name = "authorizationEventsSnsClient")
public class AuthorizationOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationOutboxPublisher.class);

    private final AuthorizationOutboxRepository repository;
    private final SnsClient snsClient;
    private final AuthorizationEventsProperties properties;
    private final ObjectMapper objectMapper;

    public AuthorizationOutboxPublisher(AuthorizationOutboxRepository repository, SnsClient snsClient,
                                        AuthorizationEventsProperties properties, ObjectMapper objectMapper) {
        this.repository = repository;
        this.snsClient = snsClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${authorization-events.publish-interval-ms:1000}")
    public void publishCommittedEvents() {
        for (AuthorizationOutboxEvent event : repository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()) {
            try {
                Map<String, Object> message = new LinkedHashMap<>(event.getPayload());
                message.put("eventId", event.getId());
                snsClient.publish(PublishRequest.builder()
                        .topicArn(properties.getTopicArn())
                        .message(objectMapper.writeValueAsString(message))
                        .messageAttributes(Map.of("eventType",
                                software.amazon.awssdk.services.sns.model.MessageAttributeValue.builder()
                                        .dataType("String").stringValue(event.getEventType()).build()))
                        .build());
                event.markPublished();
                repository.save(event);
                log.info("Authorization invalidation published [eventId={}] [eventType={}] [aggregateId={}]",
                        event.getId(), event.getEventType(), event.getAggregateId());
            } catch (Exception ex) {
                event.markFailed(safeMessage(ex));
                repository.save(event);
                log.warn("Authorization outbox delivery failed; retry scheduled "
                                + "[eventId={}] [eventType={}] [attempt={}] [errorType={}]",
                        event.getId(), event.getEventType(), event.getPublishAttempts(),
                        ex.getClass().getSimpleName());
                log.debug("Authorization outbox delivery failure detail [eventId={}]",
                        event.getId(), ex);
            }
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(512, message.length()));
    }
}
