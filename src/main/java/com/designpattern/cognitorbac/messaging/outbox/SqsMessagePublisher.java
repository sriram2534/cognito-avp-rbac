package com.designpattern.cognitorbac.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Reusable adapter that publishes an outbox envelope through AWS SDK v2. */
@Component
@ConditionalOnProperty(prefix = "messaging.outbox", name = "enabled", havingValue = "true")
public class SqsMessagePublisher {
    private static final int MAX_MESSAGE_BYTES = 240_000;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;

    public SqsMessagePublisher(SqsClient sqsClient, ObjectMapper objectMapper, OutboxProperties properties) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String publish(OutboxEvent event) {
        String body = objectMapper.writeValueAsString(OutboxEnvelope.from(event));
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Outbox event exceeds the configured SQS message safety limit");
        }
        SendMessageRequest.Builder request = SendMessageRequest.builder()
                .queueUrl(properties.getQueueUrl())
                .messageBody(body)
                .messageAttributes(attributes(event));
        if (properties.getQueueUrl().endsWith(".fifo")) {
            request.messageGroupId(properties.getFifoMessageGroupId())
                    .messageDeduplicationId(event.getEventId());
        }
        return sqsClient.sendMessage(request.build()).messageId();
    }

    private Map<String, MessageAttributeValue> attributes(OutboxEvent event) {
        return Map.of(
                "eventType", stringAttribute(event.getEventType()),
                "schemaVersion", stringAttribute(Integer.toString(event.getSchemaVersion())),
                "source", stringAttribute(event.getSource()));
    }

    private MessageAttributeValue stringAttribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }
}
