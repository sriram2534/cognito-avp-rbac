package com.designpattern.cognitorbac.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

/**
 * Service responsible for persisting and querying {@link AuditEntry} documents.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEntryRepository repository;
    private final AuditEntryMapper auditEntryMapper;
    private final AuditActionFilter auditActionFilter;

    public AuditService(AuditEntryRepository repository, AuditEntryMapper auditEntryMapper,
                        AuditActionFilter auditActionFilter) {
        this.repository = repository;
        this.auditEntryMapper = auditEntryMapper;
        this.auditActionFilter = auditActionFilter;
    }

    /**
     * Persists an authorization-source audit entry in the mutation transaction.
     * Missing identity data or persistence errors propagate so the authorization
     * mutation cannot commit without its mandatory audit record.
     */
    @Transactional
    public void recordAuthorizationChange(AuditAction action, String aggregateType, String aggregateId,
                                          String roleName, String roleKey, String permissionId,
                                          List<FieldChange> changes) {
        if (!auditActionFilter.shouldCapture(action)) {
            log.atDebug().addKeyValue("event", "audit_action_filtered")
                    .addKeyValue("auditAction", action)
                    .log("Audit action filtered");
            return;
        }
        AuditContext ctx = requiredContext();
        AuditEntry entry = auditEntryMapper.toAuthorizationEntry(
                action, aggregateType, aggregateId, roleName, roleKey, permissionId, ctx, changes);
        repository.save(entry);
        afterCommit(() -> log.atInfo()
                .addKeyValue("event", "authorization_audit_committed")
                .addKeyValue("auditAction", action)
                .addKeyValue("aggregateType", aggregateType)
                .addKeyValue("aggregateId", aggregateId)
                .addKeyValue("roleName", roleName)
                .addKeyValue("roleKey", roleKey)
                .addKeyValue("permissionId", permissionId)
                .addKeyValue("actorUserSub", ctx.getUserSub())
                .log("Authorization audit committed"));
    }

    // ─── Query API ───────────────────────────────────────────────────────────────

    public Page<AuditEntry> findByUser(String userSub, Pageable pageable) {
        return repository.findByUserSub(userSub, pageable);
    }

    public Page<AuditEntry> findByAction(AuditAction action, Pageable pageable) {
        return repository.findByAction(action, pageable);
    }

    public Page<AuditEntry> findByTimeRange(Instant from, Instant to, Pageable pageable) {
        return repository.findByOccurredAtBetween(from, to, pageable);
    }

    private AuditContext requiredContext() {
        AuditContext context = AuditContext.current();
        if (context == null || context.getUserSub() == null || context.getUserSub().isBlank()
                || context.getUserEmail() == null || context.getUserEmail().isBlank()) {
            throw new IllegalStateException("Authenticated userSub and userEmail are required for auditing");
        }
        if (context.getReason() == null || context.getReason().isBlank()) {
            throw new IllegalArgumentException(AuditContextFilter.AUDIT_REASON_HEADER + " must not be blank");
        }
        return context;
    }

    private void afterCommit(Runnable event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            event.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                event.run();
            }
        });
    }
}
