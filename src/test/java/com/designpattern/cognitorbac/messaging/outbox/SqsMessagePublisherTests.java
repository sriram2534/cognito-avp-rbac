package com.designpattern.cognitorbac.messaging.outbox;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqsMessagePublisherTests {
    @Test
    void publishesStableEnvelopeAndFifoIdentifiers() {
        SqsClient sqsClient = mock(SqsClient.class);
        OutboxProperties properties = new OutboxProperties();
        properties.setQueueUrl("https://sqs.us-east-1.amazonaws.com/123/auth.fifo");
        properties.setFifoMessageGroupId("authorization-changes");
        OutboxEvent event = OutboxEvent.pending(new OutboxMessage(
                        "nexus.authorization.changed", 1, "ROLE", "role-1", "request-1",
                        Map.of("changeType", "ROLE_DEACTIVATED")),
                "nexus-rbac-admin", Instant.parse("2026-09-06T10:00:00Z"));
        when(sqsClient.sendMessage(org.mockito.ArgumentMatchers.any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("sqs-message-1").build());
        SqsMessagePublisher publisher = new SqsMessagePublisher(sqsClient, new ObjectMapper(), properties);

        String messageId = publisher.publish(event);

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());
        SendMessageRequest request = captor.getValue();
        assertEquals("sqs-message-1", messageId);
        assertEquals("authorization-changes", request.messageGroupId());
        assertEquals(event.getEventId(), request.messageDeduplicationId());
        assertEquals("nexus.authorization.changed",
                request.messageAttributes().get("eventType").stringValue());
        assertTrue(request.messageBody().contains("\"deliverySemantics\"")
                || request.messageBody().contains("\"changeType\""));
        assertNotNull(request.messageBody());
    }
}
