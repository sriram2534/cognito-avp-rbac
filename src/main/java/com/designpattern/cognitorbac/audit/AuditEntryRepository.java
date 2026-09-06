package com.designpattern.cognitorbac.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Spring Data MongoDB repository for {@link AuditEntry}.
 * Index-backed queries for the most common audit report patterns.
 */
@Repository
public interface AuditEntryRepository extends MongoRepository<AuditEntry, String> {

    Page<AuditEntry> findByUserSub(String userSub, Pageable pageable);

    Page<AuditEntry> findByAction(AuditAction action, Pageable pageable);

    Page<AuditEntry> findByOccurredAtBetween(Instant from, Instant to, Pageable pageable);

}
