package com.designpattern.cognitorbac.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            log.debug("Audit action filtered [action={}]", action);
            return;
        }
        AuditContext ctx = requiredContext();
        AuditEntry entry = auditEntryMapper.toAuthorizationEntry(
                action, aggregateType, aggregateId, roleName, roleKey, permissionId, ctx, changes);
        repository.save(entry);
        log.info("Authorization source audited [action={}] [aggregateType={}] [aggregateId={}] "
                        + "[roleName={}] [roleKey={}] [permissionId={}] [userSub={}] [userEmail={}]",
                action, aggregateType, aggregateId, roleName, roleKey, permissionId,
                ctx.getUserSub(), ctx.getUserEmail());
    }

    // ─── Query API ───────────────────────────────────────────────────────────────

    public Page<AuditEntry> findByRole(String roleName, Pageable pageable) {
        return repository.findByRoleName(roleName, pageable);
    }

    public Page<AuditEntry> findByUser(String userSub, Pageable pageable) {
        return repository.findByUserSub(userSub, pageable);
    }

    public Page<AuditEntry> findByAction(AuditAction action, Pageable pageable) {
        return repository.findByAction(action, pageable);
    }

    public Page<AuditEntry> findByRoleAndAction(String roleName, AuditAction action, Pageable pageable) {
        return repository.findByRoleNameAndAction(roleName, action, pageable);
    }

    public Page<AuditEntry> findByTimeRange(Instant from, Instant to, Pageable pageable) {
        return repository.findByOccurredAtBetween(from, to, pageable);
    }

    public List<AuditEntry> recentForRole(String roleName) {
        return repository.findTop50ByRoleNameOrderByOccurredAtDesc(roleName);
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
}
