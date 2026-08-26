package com.designpattern.cognitorbac.outbox;

import com.designpattern.cognitorbac.audit.AuditContext;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Persists invalidation contracts in the same MongoDB transaction as source data. */
@Service
public class AuthorizationOutboxService {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationOutboxService.class);
    private final AuthorizationOutboxRepository repository;

    public AuthorizationOutboxService(AuthorizationOutboxRepository repository) {
        this.repository = repository;
    }

    public void enqueue(String eventType, String aggregateId, Map<String, Object> payload) {
        AuditContext context = AuditContext.current();
        String correlationId = context != null ? context.getCorrelationId() : null;
        AuthorizationOutboxEvent event = repository.save(
                new AuthorizationOutboxEvent(eventType, aggregateId, payload, correlationId));
        log.info("Authorization invalidation queued [eventId={}] [eventType={}] [aggregateId={}]",
                event.getId(), eventType, aggregateId);
    }
}
