package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.audit.AuthorizationAudit;
import com.designpattern.cognitorbac.audit.AuthorizationAuditOperation;
import com.designpattern.cognitorbac.dto.PermissionResponse;
import com.designpattern.cognitorbac.dto.RolePermissionResponse;
import com.designpattern.cognitorbac.exception.ResourceConflictException;
import com.designpattern.cognitorbac.exception.ResourceNotFoundException;
import com.designpattern.cognitorbac.mapper.RolePermissionMapper;
import com.designpattern.cognitorbac.mapper.PermissionMapper;
import com.designpattern.cognitorbac.permission.Permission;
import com.designpattern.cognitorbac.permission.PermissionRepository;
import com.designpattern.cognitorbac.permission.PermissionStatus;
import com.designpattern.cognitorbac.permission.RolePermission;
import com.designpattern.cognitorbac.permission.RolePermissionRepository;
import com.designpattern.cognitorbac.permission.RolePermissionStatus;
import com.designpattern.cognitorbac.role.NexusRole;
import com.designpattern.cognitorbac.role.NexusRoleRepository;
import com.designpattern.cognitorbac.role.NexusRoleStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Assigns reusable MongoDB permissions to database-owned Nexus roles.
 */
@Service
public class RolePermissionService {
    private static final Logger log = LoggerFactory.getLogger(RolePermissionService.class);
    private final RolePermissionRepository relationships;
    private final PermissionRepository permissions;
    private final NexusRoleRepository roles;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    public RolePermissionService(RolePermissionRepository relationships, PermissionRepository permissions,
                                 NexusRoleRepository roles,
                                 RolePermissionMapper rolePermissionMapper,
                                 PermissionMapper permissionMapper) {
        this.relationships = relationships;
        this.permissions = permissions;
        this.roles = roles;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
    }

    @Transactional
    @AuthorizationAudit(operation = AuthorizationAuditOperation.ROLE_PERMISSION_ASSOCIATED)
    public RolePermissionResponse grant(String roleId, String permissionId) {
        NexusRole role = requireActiveRole(roleId);
        Permission permission = permissions.findByPermissionId(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));
        if (permission.getStatus() != PermissionStatus.ACTIVE) {
            throw new ResourceConflictException("Cannot assign an inactive permission: " + permissionId);
        }
        RolePermission relationship = relationships.findByRoleIdAndPermissionId(role.getRoleId(), permissionId)
                .map(existing -> restore(existing, role))
                .orElseGet(() -> create(role, permissionId));
        return rolePermissionMapper.toResponse(relationship);
    }

    @Transactional
    @AuthorizationAudit(operation = AuthorizationAuditOperation.ROLE_PERMISSION_REVOKED)
    public void revoke(String roleId, String permissionId) {
        NexusRole role = requireRole(roleId);
        RolePermission relationship = relationships.findByRoleIdAndPermissionId(role.getRoleId(), permissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role permission relationship not found for role " + roleId));
        if (relationship.getStatus() == RolePermissionStatus.REVOKED) {
            log.info("Role permission revocation is idempotent [roleId={}] [permissionId={}]",
                    role.getRoleId(), permissionId);
            return;
        }
        relationship.revoke(PermissionService.actorSub());
        relationships.save(relationship);
        log.info("Role permission revoked [rolePermissionId={}] [roleId={}] [permissionId={}]",
                relationship.getId(), role.getRoleId(), permissionId);
    }

    public List<PermissionResponse> permissionsForRole(String roleId) {
        NexusRole role = requireRole(roleId);
        List<PermissionResponse> result = relationships.findByRoleIdAndStatus(role.getRoleId(), RolePermissionStatus.ACTIVE).stream()
                .map(RolePermission::getPermissionId)
                .map(permissions::findByPermissionId)
                .flatMap(java.util.Optional::stream)
                .map(permissionMapper::toResponse)
                .toList();
        log.debug("Role permissions listed [roleId={}] [count={}]", roleId, result.size());
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

    public boolean hasActivePermissions(String roleId) {
        return relationships.countByRoleIdAndStatus(roleId, RolePermissionStatus.ACTIVE) > 0;
    }

    private RolePermission create(NexusRole role, String permissionId) {
        RolePermission relationship = relationships.save(
                rolePermissionMapper.toEntity(role.getRoleId(), permissionId, PermissionService.actorSub()));
        log.info("Role permission granted [rolePermissionId={}] [roleId={}] [permissionId={}]",
                relationship.getId(), role.getRoleId(), permissionId);
        return relationship;
    }

    private RolePermission restore(RolePermission relationship, NexusRole role) {
        if (relationship.getStatus() == RolePermissionStatus.ACTIVE) {
            throw new ResourceConflictException("Permission is already assigned to role " + role.getRoleId());
        }
        relationship.restore(PermissionService.actorSub());
        relationships.save(relationship);
        log.info("Role permission restored [rolePermissionId={}] [roleId={}] [permissionId={}]",
                relationship.getId(), role.getRoleId(), relationship.getPermissionId());
        return relationship;
    }

    private NexusRole requireRole(String roleId) {
        return roles.findByRoleId(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
    }

    private NexusRole requireActiveRole(String roleId) {
        NexusRole role = requireRole(roleId);
        if (role.getStatus() != NexusRoleStatus.ACTIVE) {
            throw new ResourceConflictException("Role is inactive: " + roleId);
        }
        return role;
    }
}
