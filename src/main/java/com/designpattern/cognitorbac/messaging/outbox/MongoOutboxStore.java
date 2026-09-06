package com.designpattern.cognitorbac.messaging.outbox;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Atomic MongoDB claiming prevents concurrent service instances from normally sending the same row. */
@Component
public class MongoOutboxStore {
    private final MongoTemplate mongoTemplate;

    public MongoOutboxStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    OutboxEvent insert(OutboxEvent event) {
        return mongoTemplate.insert(event);
    }

    public Optional<OutboxEvent> claimNext(Instant now, Instant leaseUntil) {
        Criteria pending = new Criteria().andOperator(
                Criteria.where("status").is(OutboxStatus.PENDING),
                Criteria.where("nextAttemptAt").lte(now));
        Criteria abandoned = new Criteria().andOperator(
                Criteria.where("status").is(OutboxStatus.IN_PROGRESS),
                Criteria.where("leaseUntil").lt(now));
        Query query = Query.query(new Criteria().orOperator(pending, abandoned))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"));
        String leaseToken = UUID.randomUUID().toString();
        Update claim = new Update()
                .set("status", OutboxStatus.IN_PROGRESS)
                .set("leaseUntil", leaseUntil)
                .set("leaseToken", leaseToken)
                .inc("attempts", 1);
        return Optional.ofNullable(mongoTemplate.findAndModify(query, claim,
                FindAndModifyOptions.options().returnNew(true), OutboxEvent.class));
    }

    public boolean markPublished(OutboxEvent event, String providerMessageId, Instant publishedAt) {
        Query ownedLease = ownedLease(event);
        Update published = new Update()
                .set("status", OutboxStatus.PUBLISHED)
                .set("publishedAt", publishedAt)
                .set("providerMessageId", providerMessageId)
                .unset("leaseUntil")
                .unset("leaseToken")
                .unset("lastError");
        return mongoTemplate.updateFirst(ownedLease, published, OutboxEvent.class).getModifiedCount() == 1;
    }

    public boolean markForRetry(OutboxEvent event, Instant nextAttemptAt, String error) {
        Update retry = new Update()
                .set("status", OutboxStatus.PENDING)
                .set("nextAttemptAt", nextAttemptAt)
                .set("lastError", error)
                .unset("leaseUntil")
                .unset("leaseToken");
        return mongoTemplate.updateFirst(ownedLease(event), retry, OutboxEvent.class).getModifiedCount() == 1;
    }

    public boolean markFailed(OutboxEvent event, String error) {
        Update failed = new Update()
                .set("status", OutboxStatus.FAILED)
                .set("lastError", error)
                .unset("leaseUntil")
                .unset("leaseToken");
        return mongoTemplate.updateFirst(ownedLease(event), failed, OutboxEvent.class).getModifiedCount() == 1;
    }

    private Query ownedLease(OutboxEvent event) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("eventId").is(event.getEventId()),
                Criteria.where("status").is(OutboxStatus.IN_PROGRESS),
                Criteria.where("leaseToken").is(event.getLeaseToken())));
    }
}
