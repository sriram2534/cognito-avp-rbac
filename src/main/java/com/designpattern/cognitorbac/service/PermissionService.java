package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.audit.AuditAction;
import com.designpattern.cognitorbac.audit.AuditContext;
import com.designpattern.cognitorbac.audit.AuditService;
import com.designpattern.cognitorbac.audit.FieldChange;
import com.designpattern.cognitorbac.audit.FieldChangeMapper;
import com.designpattern.cognitorbac.dto.CreatePermissionRequest;
import com.designpattern.cognitorbac.dto.PermissionResponse;
import com.designpattern.cognitorbac.dto.UpdatePermissionRequest;
import com.designpattern.cognitorbac.exception.ResourceConflictException;
import com.designpattern.cognitorbac.exception.ResourceNotFoundException;
import com.designpattern.cognitorbac.mapper.PermissionMapper;
import com.designpattern.cognitorbac.outbox.AuthorizationOutboxService;
import com.designpattern.cognitorbac.permission.Permission;
import com.designpattern.cognitorbac.permission.PermissionRepository;
import com.designpattern.cognitorbac.permission.PermissionStatus;
import com.designpattern.cognitorbac.permission.RolePermission;
import com.designpattern.cognitorbac.permission.RolePermissionRepository;
import com.designpattern.cognitorbac.permission.RolePermissionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Owns reusable permission documents. Coordinates are immutable because they
 * are the semantic identity used by downstream authorization consumers and caches.
 */
@Service
public class PermissionService {
    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
    private static final List<String> SUPPORTED_ACCESS = List.of("read", "write", "export", "approve", "manage");

    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;
    private final AuditService auditService;
    private final AuthorizationOutboxService outboxService;
    private final PermissionMapper permissionMapper;
    private final FieldChangeMapper fieldChangeMapper;

    public PermissionService(PermissionRepository permissions, RolePermissionRepository rolePermissions,
                             AuditService auditService, AuthorizationOutboxService outboxService,
                             PermissionMapper permissionMapper, FieldChangeMapper fieldChangeMapper) {
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.permissionMapper = permissionMapper;
        this.fieldChangeMapper = fieldChangeMapper;
    }

    @Transactional
    public PermissionResponse create(CreatePermissionRequest request) {
        Coordinates coordinates = Coordinates.from(request.module(), request.resourceType(), request.access());
        if (permissions.findByModuleAndResourceTypeAndAccess(
                coordinates.module(), coordinates.resourceType(), coordinates.access()).isPresent()) {
            throw new ResourceConflictException("Permission already exists: " + coordinates.displayKey());
        }
        Permission permission = permissions.save(permissionMapper.toEntity(
                coordinates.module(), coordinates.resourceType(), coordinates.access(),
                nullableTrim(request.description()), actorSub()));
        auditService.recordAuthorizationChange(AuditAction.PERMISSION_CREATED, "PERMISSION", permission.getPermissionId(),
                null, permission.getPermissionId(), List.of(
                        fieldChangeMapper.toFieldChange("module", null, permission.getModule()),
                        fieldChangeMapper.toFieldChange("resourceType", null, permission.getResourceType()),
                        fieldChangeMapper.toFieldChange("access", null, permission.getAccess()),
                        fieldChangeMapper.toFieldChange("status", null, permission.getStatus().name())));
        publishPermissionChanged(permission);
        log.info("Permission created [permissionId={}] [coordinates={}] [status={}]",
                permission.getPermissionId(), coordinates.displayKey(), permission.getStatus());
        return permissionMapper.toResponse(permission);
    }

    public List<PermissionResponse> list() {
        List<PermissionResponse> result = permissions.findAll().stream().map(permissionMapper::toResponse).toList();
        log.debug("Permissions listed [count={}]", result.size());
        return result;
    }

    public PermissionResponse get(String permissionId) {
        Permission permission = requirePermission(permissionId);
        log.debug("Permission retrieved [permissionId={}]", permissionId);
        return permissionMapper.toResponse(permission);
    }

    @Transactional
    public PermissionResponse updateDescription(String permissionId, UpdatePermissionRequest request) {
        Permission permission = requirePermission(permissionId);
        String description = request.description().trim();
        if (description.equals(permission.getDescription())) {
            log.info("Permission description update is idempotent [permissionId={}]", permissionId);
            return permissionMapper.toResponse(permission);
        }
        String before = permission.getDescription();
        permission.updateDescription(description, actorSub());
        permissions.save(permission);
        auditService.recordAuthorizationChange(AuditAction.PERMISSION_DESCRIPTION_UPDATED, "PERMISSION",
                permission.getPermissionId(), null, permission.getPermissionId(),
                List.of(fieldChangeMapper.toFieldChange("description", before, description)));
        publishPermissionChanged(permission);
        log.info("Permission description updated [permissionId={}]", permission.getPermissionId());
        return permissionMapper.toResponse(permission);
    }

    @Transactional
    public PermissionResponse deactivate(String permissionId) {
        return changeStatus(permissionId, PermissionStatus.INACTIVE, AuditAction.PERMISSION_DEACTIVATED);
    }

    @Transactional
    public PermissionResponse activate(String permissionId) {
        return changeStatus(permissionId, PermissionStatus.ACTIVE, AuditAction.PERMISSION_ACTIVATED);
    }

    Permission requirePermission(String permissionId) {
        return permissions.findByPermissionId(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));
    }

    private PermissionResponse changeStatus(String permissionId, PermissionStatus desired, AuditAction action) {
        Permission permission = requirePermission(permissionId);
        if (permission.getStatus() == desired) {
            log.info("Permission status change is idempotent [permissionId={}] [status={}]",
                    permissionId, desired);
            return permissionMapper.toResponse(permission);
        }
        PermissionStatus before = permission.getStatus();
        permission.setStatus(desired, actorSub());
        permissions.save(permission);
        auditService.recordAuthorizationChange(action, "PERMISSION", permission.getPermissionId(),
                null, permission.getPermissionId(),
                List.of(fieldChangeMapper.toFieldChange("status", before.name(), desired.name())));
        publishPermissionChanged(permission);
        log.info("Permission status changed [permissionId={}] [from={}] [to={}]",
                permission.getPermissionId(), before, desired);
        return permissionMapper.toResponse(permission);
    }

    private void publishPermissionChanged(Permission permission) {
        List<String> affectedRoleIds = rolePermissions.findByPermissionIdAndStatus(
                        permission.getPermissionId(), RolePermissionStatus.ACTIVE)
                .stream().map(RolePermission::getRoleId).distinct().sorted().toList();
        outboxService.enqueue("PERMISSION_CHANGED", permission.getPermissionId(), Map.of(
                "eventVersion", 1,
                "eventType", "PERMISSION_CHANGED",
                "permissionId", permission.getPermissionId(),
                "affectedRoleIds", affectedRoleIds,
                "correlationId", correlationId(),
                "occurredAt", Instant.now().toString()));
        log.debug("Permission invalidation prepared [permissionId={}] [affectedRoleCount={}]",
                permission.getPermissionId(), affectedRoleIds.size());
    }

    private static String nullableTrim(String value) {
        return value == null ? null : value.trim();
    }

    static String actorSub() {
        AuditContext context = AuditContext.current();
        return context != null ? context.getActorSub() : null;
    }

    static String correlationId() {
        AuditContext context = AuditContext.current();
        return context != null && context.getCorrelationId() != null ? context.getCorrelationId() : "unknown";
    }

    private record Coordinates(String module, String resourceType, String access) {
        static Coordinates from(String module, String resourceType, String access) {
            String normalizedModule = normalize(module, "module");
            String normalizedResourceType = normalize(resourceType, "resourceType");
            String normalizedAccess = normalize(access, "access");
            if (!SUPPORTED_ACCESS.contains(normalizedAccess)) {
                throw new IllegalArgumentException("access must be one of: " + String.join(", ", SUPPORTED_ACCESS));
            }
            return new Coordinates(normalizedModule, normalizedResourceType, normalizedAccess);
        }

        String displayKey() {
            return module + ":" + resourceType + ":" + access;
        }

        private static String normalize(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " is required");
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
                throw new IllegalArgumentException(field + " must use lowercase letters, numbers, '_' or '-'");
            }
            return normalized;
        }
    }
}
