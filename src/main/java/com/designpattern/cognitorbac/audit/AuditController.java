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
     *   GET /api/v1/audit/search?groupName=ops:store:write
     *   GET /api/v1/audit/search?actorSub=uuid-123&actions=GROUP_CREATED,GROUP_UPDATED
     *   GET /api/v1/audit/search?groupName=ops:store:write&targetUsername=alice
     *   GET /api/v1/audit/search?changedField=email&from=2026-08-01T00:00:00Z
     *   GET /api/v1/audit/search?actions=USER_DISABLED,USER_ENABLED&from=2026-08-01T00:00:00Z&to=2026-08-31T23:59:59Z
     * </pre>
     * </p>
     */
    @GetMapping("/search")
    public Page<AuditEntry> search(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String targetUsername,
            @RequestParam(required = false) String actorSub,
            @RequestParam(required = false) String actorEmail,
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
                .groupName(groupName)
                .targetUsername(targetUsername)
                .actorSub(actorSub)
                .actorEmail(actorEmail)
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
     * Returns paginated audit history for a specific group.
     */
    @GetMapping("/groups/{groupName}")
    public Page<AuditEntry> byGroup(
            @PathVariable String groupName,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, clamp(size), Sort.by(Sort.Direction.DESC, "occurred_at"));

        if (action != null) {
            return auditService.findByGroupAndAction(groupName, action, pageable);
        }
        return auditService.findByGroup(groupName, pageable);
    }

    /**
     * Returns the 50 most recent audit entries for a group (convenience endpoint).
     */
    @GetMapping("/groups/{groupName}/recent")
    public List<AuditEntry> recentByGroup(@PathVariable String groupName) {
        return auditService.recentForGroup(groupName);
    }

    /**
     * Returns paginated audit history for a specific caller identified by their Cognito sub.
     */
    @GetMapping("/actors/{actorSub}")
    public Page<AuditEntry> byActor(
            @PathVariable String actorSub,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, clamp(size), Sort.by(Sort.Direction.DESC, "occurred_at"));
        return auditService.findByActor(actorSub, pageable);
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
