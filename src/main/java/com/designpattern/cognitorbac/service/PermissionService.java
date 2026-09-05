package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.audit.AuditContext;
import com.designpattern.cognitorbac.audit.AuthorizationAudit;
import com.designpattern.cognitorbac.audit.AuthorizationAuditOperation;
import com.designpattern.cognitorbac.dto.CreatePermissionRequest;
import com.designpattern.cognitorbac.dto.PermissionResponse;
import com.designpattern.cognitorbac.dto.UpdatePermissionRequest;
import com.designpattern.cognitorbac.exception.ResourceConflictException;
import com.designpattern.cognitorbac.exception.ResourceNotFoundException;
import com.designpattern.cognitorbac.mapper.PermissionMapper;
import com.designpattern.cognitorbac.permission.Permission;
import com.designpattern.cognitorbac.permission.PermissionRepository;
import com.designpattern.cognitorbac.permission.PermissionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * Owns reusable permission documents. Coordinates are immutable because they
 * are the semantic identity used by downstream authorization consumers and caches.
 */
@Service
public class PermissionService {
    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
    private static final List<String> SUPPORTED_ACCESS = List.of("read", "write", "export", "approve", "manage");

    private final PermissionRepository permissions;
    private final PermissionMapper permissionMapper;

    public PermissionService(PermissionRepository permissions, PermissionMapper permissionMapper) {
        this.permissions = permissions;
        this.permissionMapper = permissionMapper;
    }

    @Transactional
    @AuthorizationAudit(operation = AuthorizationAuditOperation.PERMISSION_CREATED)
    public PermissionResponse create(CreatePermissionRequest request) {
        Coordinates coordinates = Coordinates.from(request.module(), request.resourceType(), request.access());
        if (permissions.findByModuleAndResourceTypeAndAccess(
                coordinates.module(), coordinates.resourceType(), coordinates.access()).isPresent()) {
            throw new ResourceConflictException("Permission already exists: " + coordinates.displayKey());
        }
        Permission permission = permissions.save(permissionMapper.toEntity(
                coordinates.module(), coordinates.resourceType(), coordinates.access(),
                nullableTrim(request.description()), actorSub()));
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
        permission.updateDescription(description, actorSub());
        permissions.save(permission);
        log.info("Permission description updated [permissionId={}]", permission.getPermissionId());
        return permissionMapper.toResponse(permission);
    }

    @Transactional
    @AuthorizationAudit(operation = AuthorizationAuditOperation.PERMISSION_DEACTIVATED)
    public PermissionResponse deactivate(String permissionId) {
        return changeStatus(permissionId, PermissionStatus.INACTIVE);
    }

    @Transactional
    @AuthorizationAudit(operation = AuthorizationAuditOperation.PERMISSION_ACTIVATED)
    public PermissionResponse activate(String permissionId) {
        return changeStatus(permissionId, PermissionStatus.ACTIVE);
    }

    Permission requirePermission(String permissionId) {
        return permissions.findByPermissionId(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));
    }

    private PermissionResponse changeStatus(String permissionId, PermissionStatus desired) {
        Permission permission = requirePermission(permissionId);
        if (permission.getStatus() == desired) {
            log.info("Permission status change is idempotent [permissionId={}] [status={}]",
                    permissionId, desired);
            return permissionMapper.toResponse(permission);
        }
        PermissionStatus before = permission.getStatus();
        permission.setStatus(desired, actorSub());
        permissions.save(permission);
        log.info("Permission status changed [permissionId={}] [from={}] [to={}]",
                permission.getPermissionId(), before, desired);
        return permissionMapper.toResponse(permission);
    }

    private static String nullableTrim(String value) {
        return value == null ? null : value.trim();
    }

    static String actorSub() {
        AuditContext context = AuditContext.current();
        return context != null ? context.getUserSub() : null;
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
