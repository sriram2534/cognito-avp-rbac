package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.audit.AuthorizationAudit;
import com.designpattern.cognitorbac.audit.AuthorizationAuditOperation;
import com.designpattern.cognitorbac.dto.CreateRoleRequest;
import com.designpattern.cognitorbac.dto.RoleResponse;
import com.designpattern.cognitorbac.dto.UpdateRoleRequest;
import com.designpattern.cognitorbac.dto.UserRoleResponse;
import com.designpattern.cognitorbac.exception.ResourceConflictException;
import com.designpattern.cognitorbac.exception.ResourceNotFoundException;
import com.designpattern.cognitorbac.mapper.RoleMapper;
import com.designpattern.cognitorbac.permission.RoleKey;
import com.designpattern.cognitorbac.role.NexusRole;
import com.designpattern.cognitorbac.role.NexusRoleRepository;
import com.designpattern.cognitorbac.role.NexusRoleStatus;
import com.designpattern.cognitorbac.role.NexusUserRole;
import com.designpattern.cognitorbac.role.NexusUserRoleRepository;
import com.designpattern.cognitorbac.role.NexusUserRoleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Owns database-backed Nexus roles and user-to-role memberships. */
@Service
public class RoleService {
    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final NexusRoleRepository roles;
    private final NexusUserRoleRepository userRoles;
    private final RoleMapper roleMapper;

    public RoleService(NexusRoleRepository roles, NexusUserRoleRepository userRoles,
                       RoleMapper roleMapper) {
        this.roles = roles;
        this.userRoles = userRoles;
        this.roleMapper = roleMapper;
    }

    @Transactional
    @AuthorizationAudit(operation = AuthorizationAuditOperation.ROLE_CREATED)
    public RoleResponse create(CreateRoleRequest request) {
        RoleCoordinates coordinates = RoleCoordinates.from(request.roleKey());
        if (roles.findByRoleKey(coordinates.roleKey()).isPresent()) {
            throw new ResourceConflictException("Role already exists: " + coordinates.roleKey());
        }
        NexusRole role = roles.save(roleMapper.toRoleEntity(coordinates.roleKey(), coordinates.module(),
                coordinates.name(), trim(request.displayName()), trim(request.description()), PermissionService.actorSub()));
        log.info("Nexus role created [roleId={}] [roleKey={}]", role.getRoleId(), role.getRoleKey());
        return roleMapper.toResponse(role);
    }

    public List<RoleResponse> list() {
        return roles.findAll().stream().sorted(java.util.Comparator
                        .comparing(NexusRole::getModule).thenComparing(NexusRole::getName))
                .map(roleMapper::toResponse).toList();
    }

    public RoleResponse get(String roleId) {
        return roleMapper.toResponse(requireRole(roleId));
    }

    @Transactional
    public RoleResponse update(String roleId, UpdateRoleRequest request) {
        NexusRole role = requireRole(roleId);
        String displayName = trim(request.displayName());
        String description = trim(request.description());
        if (displayName.equals(role.getDisplayName()) && java.util.Objects.equals(description, role.getDescription())) {
            return roleMapper.toResponse(role);
        }
        role.update(displayName, description, PermissionService.actorSub());
        roles.save(role);
        return roleMapper.toResponse(role);
    }

    @Transactional
    @AuthorizationAudit(operation = AuthorizationAuditOperation.ROLE_ACTIVATED)
    public RoleResponse activate(String roleId) {
        return changeStatus(roleId, NexusRoleStatus.ACTIVE);
    }

    @Transactional
    @AuthorizationAudit(operation = AuthorizationAuditOperation.ROLE_DEACTIVATED)
    public RoleResponse deactivate(String roleId) {
        return changeStatus(roleId, NexusRoleStatus.INACTIVE);
    }

    @Transactional
    @AuthorizationAudit(operation = AuthorizationAuditOperation.USER_ROLE_ASSOCIATED)
    public List<UserRoleResponse> assignUsers(String roleId, List<String> userSubs) {
        NexusRole role = requireActiveRole(roleId);
        return userSubs.stream().distinct().map(userSub -> assignUser(role, requireUserSub(userSub))).toList();
    }

    @Transactional
    @AuthorizationAudit(operation = AuthorizationAuditOperation.USER_ROLE_REMOVED)
    public void removeUser(String roleId, String userSub) {
        NexusRole role = requireRole(roleId);
        NexusUserRole relationship = userRoles.findByUserSubAndRoleId(requireUserSub(userSub), role.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("User role relationship not found"));
        if (relationship.getStatus() == NexusUserRoleStatus.REMOVED) {
            return;
        }
        relationship.remove(PermissionService.actorSub());
        userRoles.save(relationship);
    }

    public List<UserRoleResponse> users(String roleId) {
        NexusRole role = requireRole(roleId);
        return userRoles.findByRoleIdAndStatus(role.getRoleId(), NexusUserRoleStatus.ACTIVE).stream()
                .map(roleMapper::toUserRoleResponse).toList();
    }

    public List<String> roleKeysForUser(String userSub) {
        return userRoles.findByUserSubAndStatus(userSub, NexusUserRoleStatus.ACTIVE).stream()
                .map(NexusUserRole::getRoleId)
                .map(roles::findByRoleId)
                .flatMap(java.util.Optional::stream)
                .filter(role -> role.getStatus() == NexusRoleStatus.ACTIVE)
                .map(NexusRole::getRoleKey)
                .sorted()
                .toList();
    }

    public NexusRole requireRole(String roleId) {
        return roles.findByRoleId(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
    }

    public NexusRole requireActiveRole(String roleId) {
        NexusRole role = requireRole(roleId);
        if (role.getStatus() != NexusRoleStatus.ACTIVE) {
            throw new ResourceConflictException("Role is inactive: " + roleId);
        }
        return role;
    }

    private UserRoleResponse assignUser(NexusRole role, String userSub) {
        NexusUserRole relationship = userRoles.findByUserSubAndRoleId(userSub, role.getRoleId())
                .map(existing -> restore(existing, role))
                .orElseGet(() -> create(userSub, role));
        return roleMapper.toUserRoleResponse(relationship);
    }

    private NexusUserRole create(String userSub, NexusRole role) {
        NexusUserRole relationship = userRoles.save(roleMapper.toUserRoleEntity(userSub, role.getRoleId(), PermissionService.actorSub()));
        return relationship;
    }

    private NexusUserRole restore(NexusUserRole relationship, NexusRole role) {
        if (relationship.getStatus() == NexusUserRoleStatus.ACTIVE) {
            return relationship;
        }
        relationship.restore(PermissionService.actorSub());
        userRoles.save(relationship);
        return relationship;
    }

    private RoleResponse changeStatus(String roleId, NexusRoleStatus desired) {
        NexusRole role = requireRole(roleId);
        if (role.getStatus() == desired) {
            return roleMapper.toResponse(role);
        }
        role.setStatus(desired, PermissionService.actorSub());
        roles.save(role);
        return roleMapper.toResponse(role);
    }

    private static String requireUserSub(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new IllegalArgumentException("userSub is required");
        }
        return userSub.trim();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record RoleCoordinates(String roleKey, String module, String name) {
        static RoleCoordinates from(String rawRoleKey) {
            String roleKey = RoleKey.requireCanonical(rawRoleKey);
            String[] parts = roleKey.split(":", 2);
            return new RoleCoordinates(roleKey, parts[0], parts[1]);
        }
    }
}
