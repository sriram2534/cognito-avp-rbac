package com.designpattern.cognitorbac.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data MongoDB repository for {@link AuditEntry}.
 * Index-backed queries for the most common audit report patterns.
 */
@Repository
public interface AuditEntryRepository extends MongoRepository<AuditEntry, String> {

    Page<AuditEntry> findByGroupName(String groupName, Pageable pageable);

    Page<AuditEntry> findByActorSub(String actorSub, Pageable pageable);

    Page<AuditEntry> findByAction(AuditAction action, Pageable pageable);

    Page<AuditEntry> findByOccurredAtBetween(Instant from, Instant to, Pageable pageable);

    Page<AuditEntry> findByGroupNameAndAction(String groupName, AuditAction action, Pageable pageable);

    List<AuditEntry> findTop50ByGroupNameOrderByOccurredAtDesc(String groupName);
}
