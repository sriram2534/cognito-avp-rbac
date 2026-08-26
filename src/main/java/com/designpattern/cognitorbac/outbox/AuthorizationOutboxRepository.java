package com.designpattern.cognitorbac.outbox;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuthorizationOutboxRepository extends MongoRepository<AuthorizationOutboxEvent, String> {
    List<AuthorizationOutboxEvent> findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();
}
