package com.designpattern.cognitorbac.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Read-only REST API for querying audit entries.
 * All endpoints are restricted to authenticated callers.
 */
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("isAuthenticated()")
public class AuditController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditService auditService;
    private final AuditQueryService queryService;

    public AuditController(AuditService auditService, AuditQueryService queryService) {
        this.auditService = auditService;
        this.queryService = queryService;
    }

    /**
     * Composable search — any combination of filters, all optional.
     *
     * <p>Examples:
     * <pre>
     *   GET /api/v1/audit/search?roleKey=ops:developer
     *   GET /api/v1/audit/search?userSub=uuid-123&actions=ROLE_CREATED,ROLE_DEACTIVATED
     *   GET /api/v1/audit/search?changedField=email&from=2026-08-01T00:00:00Z
     *   GET /api/v1/audit/search?actions=USER_ROLE_ASSIGNED,USER_ROLE_REMOVED&from=2026-08-01T00:00:00Z&to=2026-08-31T23:59:59Z
     * </pre>
     * </p>
     */
    @GetMapping("/search")
    public Page<AuditEntry> search(
            @RequestParam(required = false) String userSub,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String roleKey,
            @RequestParam(required = false) String permissionId,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) List<AuditAction> actions,
            @RequestParam(required = false) String changedField,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditFilter filter = AuditFilter.builder()
                .userSub(userSub)
                .userEmail(userEmail)
                .roleName(roleName)
                .roleKey(roleKey)
                .permissionId(permissionId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .actions(actions)
                .changedField(changedField)
                .from(from)
                .to(to)
                .build();

        PageRequest pageable = PageRequest.of(page, clamp(size), Sort.by(Sort.Direction.DESC, "occurred_at"));
        return queryService.search(filter, pageable);
    }

    /**
     * Returns paginated audit history for a specific role key.
     */
    @GetMapping("/roles/{roleKey}")
    public Page<AuditEntry> byRole(
            @PathVariable String roleKey,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, clamp(size), Sort.by(Sort.Direction.DESC, "occurred_at"));

        if (action != null) {
            return queryService.search(AuditFilter.builder().roleKey(roleKey).actions(List.of(action)).build(), pageable);
        }
        return queryService.search(AuditFilter.builder().roleKey(roleKey).build(), pageable);
    }

    /**
     * Returns the 50 most recent audit entries for a role (convenience endpoint).
     */
    @GetMapping("/roles/{roleKey}/recent")
    public List<AuditEntry> recentByRole(@PathVariable String roleKey) {
        return queryService.search(AuditFilter.builder().roleKey(roleKey).build(),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "occurred_at"))).getContent();
    }

    /**
     * Returns paginated audit history for a specific caller identified by their Cognito sub.
     */
    @GetMapping("/users/{userSub}")
    public Page<AuditEntry> byUser(
            @PathVariable String userSub,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, clamp(size), Sort.by(Sort.Direction.DESC, "occurred_at"));
        return auditService.findByUser(userSub, pageable);
    }

    /**
     * Returns paginated audit entries filtered by action type.
     */
    @GetMapping("/actions/{action}")
    public Page<AuditEntry> byAction(
            @PathVariable AuditAction action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, clamp(size), Sort.by(Sort.Direction.DESC, "occurred_at"));
        return auditService.findByAction(action, pageable);
    }

    /**
     * Returns paginated audit entries within a time range (ISO-8601 instants).
     */
    @GetMapping
    public Page<AuditEntry> byTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        PageRequest pageable = PageRequest.of(page, clamp(size), Sort.by(Sort.Direction.DESC, "occurred_at"));
        return auditService.findByTimeRange(from, to, pageable);
    }

    private int clamp(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
