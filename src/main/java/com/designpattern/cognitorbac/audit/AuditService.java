package com.designpattern.cognitorbac.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service responsible for persisting and querying {@link AuditEntry} documents.
 *
 * <p>Recording is fire-and-forget: failures are logged but never propagate to
 * the caller so that an audit write failure never blocks a business operation.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEntryRepository repository;
    private final AuditEntryMapper auditEntryMapper;

    public AuditService(AuditEntryRepository repository, AuditEntryMapper auditEntryMapper) {
        this.repository = repository;
        this.auditEntryMapper = auditEntryMapper;
    }

    /**
     * Records an audit entry. Uses {@link AuditContext} for caller identity and reason.
     * Never throws — failures are swallowed and logged.
     *
     * @param action       the action being audited
     * @param groupName      the target group (null for user-only operations)
     * @param targetUsername the affected user (null for group-only operations)
     * @param changes        field-level before/after deltas; empty list for membership/creation events
     */
    public void record(AuditAction action, String groupName, String targetUsername,
                       List<FieldChange> changes) {
        MDC.put("auditAction", action.name());
        try {
            AuditContext ctx = AuditContext.current();
            if (ctx == null) {
                log.warn("AuditContext missing — AuditContextFilter may have been bypassed");
            }
            AuditEntry entry = auditEntryMapper.toLegacyEntry(action, groupName, targetUsername, ctx, changes);
            repository.save(entry);
            log.debug("Audit entry persisted: groupName={} targetUsername={} changesCount={}",
                    groupName, targetUsername, changes.size());
        } catch (DataAccessException ex) {
            log.error("MongoDB write failed for audit entry — entry lost: groupName={}", groupName, ex);
        } catch (Exception ex) {
            log.error("Unexpected failure recording audit entry: groupName={}", groupName, ex);
        } finally {
            MDC.remove("auditAction");
        }
    }

    public void record(AuditAction action, String groupName) {
        record(action, groupName, null, List.of());
    }

    public void record(AuditAction action, String groupName, String targetUsername) {
        record(action, groupName, targetUsername, List.of());
    }

    /**
     * Required audit write for authorization-source mutations. Unlike legacy
     * best-effort group audit, this participates in the caller's MongoDB
     * transaction so a permission relationship never succeeds without audit.
     */
    @Transactional
    public void recordAuthorizationChange(AuditAction action, String aggregateType, String aggregateId,
                                          String roleKey, String permissionId,
                                          List<FieldChange> changes) {
        AuditContext ctx = AuditContext.current();
        AuditEntry entry = auditEntryMapper.toAuthorizationEntry(
                action, aggregateType, aggregateId, roleKey, permissionId, ctx, changes);
        repository.save(entry);
        log.info("Authorization source audited [action={}] [aggregateType={}] [aggregateId={}] "
                        + "[roleKey={}] [permissionId={}]",
                action, aggregateType, aggregateId, roleKey, permissionId);
    }

    // ─── Query API ───────────────────────────────────────────────────────────────

    public Page<AuditEntry> findByGroup(String groupName, Pageable pageable) {
        return repository.findByGroupName(groupName, pageable);
    }

    public Page<AuditEntry> findByActor(String actorSub, Pageable pageable) {
        return repository.findByActorSub(actorSub, pageable);
    }

    public Page<AuditEntry> findByAction(AuditAction action, Pageable pageable) {
        return repository.findByAction(action, pageable);
    }

    public Page<AuditEntry> findByGroupAndAction(String groupName, AuditAction action, Pageable pageable) {
        return repository.findByGroupNameAndAction(groupName, action, pageable);
    }

    public Page<AuditEntry> findByTimeRange(Instant from, Instant to, Pageable pageable) {
        return repository.findByOccurredAtBetween(from, to, pageable);
    }

    public List<AuditEntry> recentForGroup(String groupName) {
        return repository.findTop50ByGroupNameOrderByOccurredAtDesc(groupName);
    }
}
