package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.audit.AuditAction;
import com.designpattern.cognitorbac.audit.AuditService;
import com.designpattern.cognitorbac.audit.FieldChange;
import com.designpattern.cognitorbac.audit.FieldChangeMapper;
import com.designpattern.cognitorbac.dto.PermissionResponse;
import com.designpattern.cognitorbac.dto.RolePermissionResponse;
import com.designpattern.cognitorbac.exception.ResourceConflictException;
import com.designpattern.cognitorbac.exception.ResourceNotFoundException;
import com.designpattern.cognitorbac.mapper.RolePermissionMapper;
import com.designpattern.cognitorbac.mapper.PermissionMapper;
import com.designpattern.cognitorbac.outbox.AuthorizationOutboxService;
import com.designpattern.cognitorbac.permission.Permission;
import com.designpattern.cognitorbac.permission.PermissionRepository;
import com.designpattern.cognitorbac.permission.PermissionStatus;
import com.designpattern.cognitorbac.permission.RoleKey;
import com.designpattern.cognitorbac.permission.RolePermission;
import com.designpattern.cognitorbac.permission.RolePermissionRepository;
import com.designpattern.cognitorbac.permission.RolePermissionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Assigns reusable MongoDB permissions to Cognito groups. No role collection is
 * created: Cognito remains authoritative for role existence and membership.
 */
@Service
public class RolePermissionService {
    private static final Logger log = LoggerFactory.getLogger(RolePermissionService.class);
    private final RolePermissionRepository relationships;
    private final PermissionRepository permissions;
    private final GroupService groupService;
    private final AuditService auditService;
    private final AuthorizationOutboxService outboxService;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final FieldChangeMapper fieldChangeMapper;

    public RolePermissionService(RolePermissionRepository relationships, PermissionRepository permissions,
                                 GroupService groupService, AuditService auditService,
                                 AuthorizationOutboxService outboxService,
                                 RolePermissionMapper rolePermissionMapper,
                                 PermissionMapper permissionMapper,
                                 FieldChangeMapper fieldChangeMapper) {
        this.relationships = relationships;
        this.permissions = permissions;
        this.groupService = groupService;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.fieldChangeMapper = fieldChangeMapper;
    }

    @Transactional
    public RolePermissionResponse grant(String roleKey, String permissionId) {
        String canonicalRoleKey = RoleKey.requireCanonical(roleKey);
        groupService.requireRole(canonicalRoleKey);
        Permission permission = permissions.findByPermissionId(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));
        if (permission.getStatus() != PermissionStatus.ACTIVE) {
            throw new ResourceConflictException("Cannot assign an inactive permission: " + permissionId);
        }
        RolePermission relationship = relationships.findByRoleKeyAndPermissionId(canonicalRoleKey, permissionId)
                .map(existing -> restore(existing, canonicalRoleKey))
                .orElseGet(() -> create(canonicalRoleKey, permissionId));
        return rolePermissionMapper.toResponse(relationship);
    }

    @Transactional
    public void revoke(String roleKey, String permissionId) {
        String canonicalRoleKey = RoleKey.requireCanonical(roleKey);
        RolePermission relationship = relationships.findByRoleKeyAndPermissionId(canonicalRoleKey, permissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role permission relationship not found for role " + canonicalRoleKey));
        if (relationship.getStatus() == RolePermissionStatus.REVOKED) {
            log.info("Role permission revocation is idempotent [roleKey={}] [permissionId={}]",
                    canonicalRoleKey, permissionId);
            return;
        }
        relationship.revoke(PermissionService.actorSub());
        relationships.save(relationship);
        auditService.recordAuthorizationChange(AuditAction.ROLE_PERMISSION_REVOKED, "ROLE_PERMISSION",
                relationship.getId(), canonicalRoleKey, permissionId,
                List.of(fieldChangeMapper.toFieldChange("status", "ACTIVE", "REVOKED")));
        publishRolePermissionsChanged(relationship);
        log.info("Role permission revoked [rolePermissionId={}] [roleKey={}] [permissionId={}]",
                relationship.getId(), canonicalRoleKey, permissionId);
    }

    public List<PermissionResponse> permissionsForRole(String roleKey) {
        String canonicalRoleKey = RoleKey.requireCanonical(roleKey);
        List<PermissionResponse> result = relationships.findByRoleKeyAndStatus(canonicalRoleKey, RolePermissionStatus.ACTIVE).stream()
                .map(RolePermission::getPermissionId)
                .map(permissions::findByPermissionId)
                .flatMap(java.util.Optional::stream)
                .map(permissionMapper::toResponse)
                .toList();
        log.debug("Role permissions listed [roleKey={}] [count={}]", canonicalRoleKey, result.size());
        return result;
    }

    public List<RolePermissionResponse> rolesForPermission(String permissionId) {
        if (!permissions.existsByPermissionId(permissionId)) {
            throw new ResourceNotFoundException("Permission not found: " + permissionId);
        }
        List<RolePermissionResponse> result = relationships.findByPermissionIdAndStatus(permissionId, RolePermissionStatus.ACTIVE).stream()
                .map(rolePermissionMapper::toResponse).toList();
        log.debug("Roles for permission listed [permissionId={}] [count={}]", permissionId, result.size());
        return result;
    }

    public boolean hasActivePermissions(String roleKey) {
        return relationships.countByRoleKeyAndStatus(roleKey, RolePermissionStatus.ACTIVE) > 0;
    }

    private RolePermission create(String roleKey, String permissionId) {
        RolePermission relationship = relationships.save(
                rolePermissionMapper.toEntity(roleKey, permissionId, PermissionService.actorSub()));
        auditService.recordAuthorizationChange(AuditAction.ROLE_PERMISSION_GRANTED, "ROLE_PERMISSION",
                relationship.getId(), roleKey, permissionId,
                List.of(fieldChangeMapper.toFieldChange("status", null, "ACTIVE")));
        publishRolePermissionsChanged(relationship);
        log.info("Role permission granted [rolePermissionId={}] [roleKey={}] [permissionId={}]",
                relationship.getId(), roleKey, permissionId);
        return relationship;
    }

    private RolePermission restore(RolePermission relationship, String roleKey) {
        if (relationship.getStatus() == RolePermissionStatus.ACTIVE) {
            throw new ResourceConflictException("Permission is already assigned to role " + roleKey);
        }
        relationship.restore(PermissionService.actorSub());
        relationships.save(relationship);
        auditService.recordAuthorizationChange(AuditAction.ROLE_PERMISSION_RESTORED, "ROLE_PERMISSION",
                relationship.getId(), roleKey, relationship.getPermissionId(),
                List.of(fieldChangeMapper.toFieldChange("status", "REVOKED", "ACTIVE")));
        publishRolePermissionsChanged(relationship);
        log.info("Role permission restored [rolePermissionId={}] [roleKey={}] [permissionId={}]",
                relationship.getId(), roleKey, relationship.getPermissionId());
        return relationship;
    }

    private void publishRolePermissionsChanged(RolePermission relationship) {
        outboxService.enqueue("ROLE_PERMISSIONS_CHANGED", relationship.getRoleKey(), Map.of(
                "eventVersion", 1,
                "eventType", "ROLE_PERMISSIONS_CHANGED",
                "roleKey", relationship.getRoleKey(),
                "rolePermissionVersion", relationship.getVersion() == null ? 0L : relationship.getVersion(),
                "correlationId", PermissionService.correlationId(),
                "occurredAt", Instant.now().toString()));
        log.debug("Role permission invalidation prepared [roleKey={}] [rolePermissionId={}]",
                relationship.getRoleKey(), relationship.getId());
    }
}
