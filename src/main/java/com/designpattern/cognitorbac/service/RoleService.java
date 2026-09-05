package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.audit.AuditAction;
import com.designpattern.cognitorbac.audit.AuditContext;
import com.designpattern.cognitorbac.audit.AuditService;
import com.designpattern.cognitorbac.audit.FieldChange;
import com.designpattern.cognitorbac.audit.FieldChangeMapper;
import com.designpattern.cognitorbac.dto.CreateRoleRequest;
import com.designpattern.cognitorbac.dto.RoleResponse;
import com.designpattern.cognitorbac.dto.UpdateRoleRequest;
import com.designpattern.cognitorbac.dto.UserRoleResponse;
import com.designpattern.cognitorbac.exception.ResourceConflictException;
import com.designpattern.cognitorbac.exception.ResourceNotFoundException;
import com.designpattern.cognitorbac.mapper.RoleMapper;
import com.designpattern.cognitorbac.outbox.AuthorizationOutboxService;
import com.designpattern.cognitorbac.permission.RoleKey;
import com.designpattern.cognitorbac.permission.RolePermissionRepository;
import com.designpattern.cognitorbac.permission.RolePermissionStatus;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns database-backed Nexus roles and user-to-role memberships. */
@Service
public class RoleService {
    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final NexusRoleRepository roles;
    private final NexusUserRoleRepository userRoles;
    private final RolePermissionRepository rolePermissions;
    private final RoleMapper roleMapper;
    private final AuditService auditService;
    private final AuthorizationOutboxService outboxService;
    private final FieldChangeMapper fieldChangeMapper;

    public RoleService(NexusRoleRepository roles, NexusUserRoleRepository userRoles,
                       RolePermissionRepository rolePermissions, RoleMapper roleMapper,
                       AuditService auditService, AuthorizationOutboxService outboxService,
                       FieldChangeMapper fieldChangeMapper) {
        this.roles = roles;
        this.userRoles = userRoles;
        this.rolePermissions = rolePermissions;
        this.roleMapper = roleMapper;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.fieldChangeMapper = fieldChangeMapper;
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest request) {
        RoleCoordinates coordinates = RoleCoordinates.from(request.roleKey());
        if (roles.findByRoleKey(coordinates.roleKey()).isPresent()) {
            throw new ResourceConflictException("Role already exists: " + coordinates.roleKey());
        }
        NexusRole role = roles.save(roleMapper.toRoleEntity(coordinates.roleKey(), coordinates.module(),
                coordinates.name(), trim(request.displayName()), trim(request.description()), PermissionService.actorSub()));
        recordRoleAudit(AuditAction.ROLE_CREATED, role, List.of(
                fieldChangeMapper.toFieldChange("roleKey", null, role.getRoleKey()),
                fieldChangeMapper.toFieldChange("status", null, role.getStatus().name())));
        publishRoleChanged(role, "CREATED");
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
        List<FieldChange> changes = new java.util.ArrayList<>();
        if (!displayName.equals(role.getDisplayName())) {
            changes.add(fieldChangeMapper.toFieldChange("displayName", role.getDisplayName(), displayName));
        }
        if (!java.util.Objects.equals(description, role.getDescription())) {
            changes.add(fieldChangeMapper.toFieldChange("description", role.getDescription(), description));
        }
        role.update(displayName, description, PermissionService.actorSub());
        roles.save(role);
        recordRoleAudit(AuditAction.ROLE_UPDATED, role, changes);
        publishRoleChanged(role, "UPDATED");
        return roleMapper.toResponse(role);
    }

    @Transactional
    public RoleResponse activate(String roleId) {
        return changeStatus(roleId, NexusRoleStatus.ACTIVE, AuditAction.ROLE_ACTIVATED);
    }

    @Transactional
    public RoleResponse deactivate(String roleId) {
        return changeStatus(roleId, NexusRoleStatus.INACTIVE, AuditAction.ROLE_DEACTIVATED);
    }

    @Transactional
    public List<UserRoleResponse> assignUsers(String roleId, List<String> userSubs) {
        NexusRole role = requireActiveRole(roleId);
        return userSubs.stream().distinct().map(userSub -> assignUser(role, requireUserSub(userSub))).toList();
    }

    @Transactional
    public void removeUser(String roleId, String userSub) {
        NexusRole role = requireRole(roleId);
        NexusUserRole relationship = userRoles.findByUserSubAndRoleId(requireUserSub(userSub), role.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("User role relationship not found"));
        if (relationship.getStatus() == NexusUserRoleStatus.REMOVED) {
            return;
        }
        relationship.remove(PermissionService.actorSub());
        userRoles.save(relationship);
        recordMembershipAudit(AuditAction.USER_ROLE_REMOVED, role, relationship, "REMOVED");
        publishMembershipChanged(role, relationship, "REMOVED");
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
        recordMembershipAudit(AuditAction.USER_ROLE_ASSIGNED, role, relationship, "ADDED");
        publishMembershipChanged(role, relationship, "ADDED");
        return relationship;
    }

    private NexusUserRole restore(NexusUserRole relationship, NexusRole role) {
        if (relationship.getStatus() == NexusUserRoleStatus.ACTIVE) {
            return relationship;
        }
        relationship.restore(PermissionService.actorSub());
        userRoles.save(relationship);
        recordMembershipAudit(AuditAction.USER_ROLE_RESTORED, role, relationship, "ADDED");
        publishMembershipChanged(role, relationship, "ADDED");
        return relationship;
    }

    private RoleResponse changeStatus(String roleId, NexusRoleStatus desired, AuditAction action) {
        NexusRole role = requireRole(roleId);
        if (role.getStatus() == desired) {
            return roleMapper.toResponse(role);
        }
        NexusRoleStatus before = role.getStatus();
        role.setStatus(desired, PermissionService.actorSub());
        roles.save(role);
        recordRoleAudit(action, role, List.of(fieldChangeMapper.toFieldChange("status", before.name(), desired.name())));
        publishRoleChanged(role, desired.name());
        return roleMapper.toResponse(role);
    }

    private void recordRoleAudit(AuditAction action, NexusRole role, List<FieldChange> changes) {
        auditService.recordAuthorizationChange(action, "ROLE", role.getRoleId(), role.getRoleKey(), null, changes);
        publishAuditRecorded(action, role.getRoleId(), role.getRoleKey(), null);
    }

    private void recordMembershipAudit(AuditAction action, NexusRole role, NexusUserRole relationship, String operation) {
        auditService.recordAuthorizationChange(action, "USER_ROLE", relationship.getUserSub() + ":" + role.getRoleId(), role.getRoleKey(), null,
                List.of(fieldChangeMapper.toFieldChange("userSub", null, relationship.getUserSub()),
                        fieldChangeMapper.toFieldChange("operation", null, operation)));
        publishAuditRecorded(action, relationship.getUserSub(), role.getRoleKey(), relationship.getUserSub());
    }

    private void publishRoleChanged(NexusRole role, String operation) {
        outboxService.enqueue("ROLE_CHANGED", role.getRoleId(), Map.of(
                "eventVersion", 1, "eventType", "ROLE_CHANGED", "roleId", role.getRoleId(),
                "roleKey", role.getRoleKey(), "operation", operation,
                "roleVersion", role.getVersion() == null ? 0L : role.getVersion(),
                "correlationId", PermissionService.correlationId(), "occurredAt", Instant.now().toString()));
    }

    private void publishMembershipChanged(NexusRole role, NexusUserRole relationship, String operation) {
        outboxService.enqueue("USER_ROLE_MEMBERSHIP_CHANGED", relationship.getUserSub(), Map.of(
                "eventVersion", 2, "eventType", "USER_ROLE_MEMBERSHIP_CHANGED", "userSub", relationship.getUserSub(),
                "roleId", role.getRoleId(), "roleKey", role.getRoleKey(), "operation", operation,
                "roleMembershipVersion", relationship.getVersion() == null ? 0L : relationship.getVersion(),
                "correlationId", PermissionService.correlationId(), "occurredAt", Instant.now().toString()));
    }

    private void publishAuditRecorded(AuditAction action, String aggregateId, String roleKey, String userSub) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventVersion", 1);
        payload.put("eventType", "AUDIT_RECORDED");
        payload.put("action", action.name());
        payload.put("aggregateId", aggregateId);
        payload.put("roleKey", roleKey);
        payload.put("correlationId", PermissionService.correlationId());
        payload.put("occurredAt", Instant.now().toString());
        AuditContext context = AuditContext.current();
        if (userSub != null) payload.put("userSub", userSub);
        if (context != null) {
            if (context.getActorSub() != null) payload.put("actorSub", context.getActorSub());
            if (context.getReason() != null) payload.put("reason", context.getReason());
        }
        outboxService.enqueue("AUDIT_RECORDED", aggregateId, payload);
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
