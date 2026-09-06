package com.designpattern.cognitorbac.audit;

import com.designpattern.cognitorbac.dto.PermissionResponse;
import com.designpattern.cognitorbac.dto.RolePermissionResponse;
import com.designpattern.cognitorbac.dto.RoleResponse;
import com.designpattern.cognitorbac.dto.UserRoleResponse;
import com.designpattern.cognitorbac.permission.PermissionRepository;
import com.designpattern.cognitorbac.permission.PermissionStatus;
import com.designpattern.cognitorbac.permission.RolePermission;
import com.designpattern.cognitorbac.permission.RolePermissionRepository;
import com.designpattern.cognitorbac.permission.RolePermissionStatus;
import com.designpattern.cognitorbac.role.NexusRole;
import com.designpattern.cognitorbac.role.NexusRoleRepository;
import com.designpattern.cognitorbac.role.NexusRoleStatus;
import com.designpattern.cognitorbac.role.NexusUserRole;
import com.designpattern.cognitorbac.role.NexusUserRoleRepository;
import com.designpattern.cognitorbac.role.NexusUserRoleStatus;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures authorization audits after an annotated service mutation returns
 * successfully. It is deliberately internal: it creates no queue messages or
 * outbox records.
 *
 * <p>The aspect opens the outer MongoDB transaction, captures before-state,
 * executes the service mutation, and persists the audit record before commit.
 * Any audit failure rolls the entire mutation back. The in-transaction snapshot
 * also prevents idempotent or concurrent requests from producing false audit
 * entries.</p>
 */
@Aspect
@Component
public class AuthorizationAuditAspect {

    private final AuditService auditService;
    private final FieldChangeMapper fieldChangeMapper;
    private final NexusRoleRepository roles;
    private final NexusUserRoleRepository userRoles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;
    private final TransactionTemplate transactionTemplate;

    public AuthorizationAuditAspect(AuditService auditService, FieldChangeMapper fieldChangeMapper,
                                   NexusRoleRepository roles, NexusUserRoleRepository userRoles,
                                   PermissionRepository permissions, RolePermissionRepository rolePermissions,
                                   PlatformTransactionManager transactionManager) {
        this.auditService = auditService;
        this.fieldChangeMapper = fieldChangeMapper;
        this.roles = roles;
        this.userRoles = userRoles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Around("@annotation(authorizationAudit)")
    public Object auditSuccessfulMutation(ProceedingJoinPoint joinPoint, AuthorizationAudit authorizationAudit)
            throws Throwable {
        try {
            return transactionTemplate.execute(status -> invokeAudited(joinPoint, authorizationAudit.operation()));
        } catch (AuditedInvocationException ex) {
            throw ex.getCause();
        }
    }

    private Object invokeAudited(ProceedingJoinPoint joinPoint, AuthorizationAuditOperation operation) {
        AuditSnapshot snapshot = snapshot(operation, joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            record(operation, snapshot, result);
            return result;
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new AuditedInvocationException(ex);
        }
    }

    private AuditSnapshot snapshot(AuthorizationAuditOperation operation, Object[] args) {
        return switch (operation) {
            case ROLE_CREATED, PERMISSION_CREATED -> AuditSnapshot.empty();
            case ROLE_ACTIVATED, ROLE_DEACTIVATED -> roleSnapshot(stringArg(args, 0));
            case USER_ROLE_ASSOCIATED -> userRoleAssociationSnapshot(stringArg(args, 0), stringListArg(args, 1));
            case USER_ROLE_REMOVED -> userRoleSnapshot(stringArg(args, 0), stringArg(args, 1));
            case PERMISSION_ACTIVATED, PERMISSION_DEACTIVATED -> permissionSnapshot(stringArg(args, 0));
            case ROLE_PERMISSION_ASSOCIATED, ROLE_PERMISSION_REVOKED -> rolePermissionSnapshot(
                    stringArg(args, 0), stringArg(args, 1));
        };
    }

    private void record(AuthorizationAuditOperation operation, AuditSnapshot snapshot, Object result) {
        switch (operation) {
            case ROLE_CREATED -> recordCreatedRole((RoleResponse) result);
            case ROLE_ACTIVATED, ROLE_DEACTIVATED -> recordRoleStatusChange(operation, snapshot, (RoleResponse) result);
            case USER_ROLE_ASSOCIATED -> recordUserRoleAssociations(snapshot, userRoleResponses(result));
            case USER_ROLE_REMOVED -> recordUserRoleRemoval(snapshot);
            case PERMISSION_CREATED -> recordCreatedPermission((PermissionResponse) result);
            case PERMISSION_ACTIVATED, PERMISSION_DEACTIVATED -> recordPermissionStatusChange(
                    operation, snapshot, (PermissionResponse) result);
            case ROLE_PERMISSION_ASSOCIATED -> recordRolePermissionAssociation(snapshot, (RolePermissionResponse) result);
            case ROLE_PERMISSION_REVOKED -> recordRolePermissionRevocation(snapshot);
        }
    }

    private AuditSnapshot roleSnapshot(String roleId) {
        return roles.findByRoleId(roleId)
                .map(role -> new AuditSnapshot(role.getName(), role.getRoleKey(), null, null, null,
                        role.getStatus(), null, null, null, Map.of()))
                .orElseGet(AuditSnapshot::empty);
    }

    private AuditSnapshot userRoleAssociationSnapshot(String roleId, List<String> userSubs) {
        NexusRole role = roles.findByRoleId(roleId).orElse(null);
        Map<String, NexusUserRoleStatus> statuses = new HashMap<>();
        if (role != null) {
            List<String> normalizedUserSubs = userSubs.stream().filter(value -> value != null && !value.isBlank())
                    .map(String::trim).distinct().toList();
            userRoles.findByRoleIdAndUserSubIn(role.getRoleId(), normalizedUserSubs)
                    .forEach(relationship -> statuses.put(relationship.getUserSub(), relationship.getStatus()));
        }
        return new AuditSnapshot(role == null ? null : role.getName(), role == null ? null : role.getRoleKey(),
                roleId, null, null, null, null, null, null, statuses);
    }

    private AuditSnapshot userRoleSnapshot(String roleId, String userSub) {
        NexusRole role = roles.findByRoleId(roleId).orElse(null);
        NexusUserRoleStatus status = role == null ? null : userRoles.findByUserSubAndRoleId(userSub, role.getRoleId())
                .map(NexusUserRole::getStatus).orElse(null);
        return new AuditSnapshot(role == null ? null : role.getName(), role == null ? null : role.getRoleKey(),
                roleId, userSub, null, null, status, null, null, Map.of());
    }

    private AuditSnapshot permissionSnapshot(String permissionId) {
        return permissions.findByPermissionId(permissionId)
                .map(permission -> new AuditSnapshot(null, null, null, null, permission.getPermissionId(), null, null,
                        permission.getStatus(), null, Map.of()))
                .orElseGet(AuditSnapshot::empty);
    }

    private AuditSnapshot rolePermissionSnapshot(String roleId, String permissionId) {
        NexusRole role = roles.findByRoleId(roleId).orElse(null);
        RolePermissionStatus status = role == null ? null
                : rolePermissions.findByRoleIdAndPermissionId(role.getRoleId(), permissionId)
                .map(RolePermission::getStatus).orElse(null);
        return new AuditSnapshot(role == null ? null : role.getName(), role == null ? null : role.getRoleKey(),
                roleId, null, permissionId, null, null, null, status, Map.of());
    }

    private void recordCreatedRole(RoleResponse role) {
        record(AuditAction.ROLE_CREATED, "ROLE", role.roleId(), role.name(), role.roleKey(), null, List.of(
                change("roleKey", null, role.roleKey()), change("status", null, role.status().name())));
    }

    private void recordRoleStatusChange(AuthorizationAuditOperation operation, AuditSnapshot snapshot, RoleResponse role) {
        if (snapshot.roleStatus() == null || snapshot.roleStatus() == role.status()) {
            return;
        }
        AuditAction action = operation == AuthorizationAuditOperation.ROLE_ACTIVATED
                ? AuditAction.ROLE_ACTIVATED : AuditAction.ROLE_DEACTIVATED;
        record(action, "ROLE", role.roleId(), role.name(), role.roleKey(), null,
                List.of(change("status", snapshot.roleStatus().name(), role.status().name())));
    }

    private void recordUserRoleAssociations(AuditSnapshot snapshot, List<UserRoleResponse> relationships) {
        for (UserRoleResponse relationship : relationships) {
            NexusUserRoleStatus before = snapshot.userRoleStatuses().get(relationship.userSub());
            if (before == NexusUserRoleStatus.ACTIVE) {
                continue;
            }
            AuditAction action = before == NexusUserRoleStatus.REMOVED
                    ? AuditAction.USER_ROLE_RESTORED : AuditAction.USER_ROLE_ASSIGNED;
            record(action, "USER_ROLE", relationship.userSub() + ":" + relationship.roleId(),
                    snapshot.roleName(), snapshot.roleKey(), null,
                    List.of(change("targetUserSub", null, relationship.userSub()), change("operation", null, "ADDED")));
        }
    }

    private void recordUserRoleRemoval(AuditSnapshot snapshot) {
        if (snapshot.userRoleStatus() != NexusUserRoleStatus.ACTIVE) {
            return;
        }
        record(AuditAction.USER_ROLE_REMOVED, "USER_ROLE", snapshot.userSub() + ":" + snapshot.roleId(),
                snapshot.roleName(), snapshot.roleKey(), null,
                List.of(change("targetUserSub", null, snapshot.userSub()), change("operation", null, "REMOVED")));
    }

    private void recordCreatedPermission(PermissionResponse permission) {
        record(AuditAction.PERMISSION_CREATED, "PERMISSION", permission.permissionId(), null, null,
                permission.permissionId(), List.of(
                change("module", null, permission.module()), change("resourceType", null, permission.resourceType()),
                change("access", null, permission.access()), change("status", null, permission.status().name())));
    }

    private void recordPermissionStatusChange(AuthorizationAuditOperation operation, AuditSnapshot snapshot,
                                              PermissionResponse permission) {
        if (snapshot.permissionStatus() == null || snapshot.permissionStatus() == permission.status()) {
            return;
        }
        AuditAction action = operation == AuthorizationAuditOperation.PERMISSION_ACTIVATED
                ? AuditAction.PERMISSION_ACTIVATED : AuditAction.PERMISSION_DEACTIVATED;
        record(action, "PERMISSION", permission.permissionId(), null, null, permission.permissionId(),
                List.of(change("status", snapshot.permissionStatus().name(), permission.status().name())));
    }

    private void recordRolePermissionAssociation(AuditSnapshot snapshot, RolePermissionResponse relationship) {
        RolePermissionStatus before = snapshot.rolePermissionStatus();
        if (before == RolePermissionStatus.ACTIVE) {
            return;
        }
        AuditAction action = before == RolePermissionStatus.REVOKED
                ? AuditAction.ROLE_PERMISSION_RESTORED : AuditAction.ROLE_PERMISSION_GRANTED;
        record(action, "ROLE_PERMISSION", relationship.roleId() + ":" + relationship.permissionId(),
                snapshot.roleName(), snapshot.roleKey(), relationship.permissionId(),
                List.of(change("status", before == null ? null : before.name(), "ACTIVE")));
    }

    private void recordRolePermissionRevocation(AuditSnapshot snapshot) {
        RolePermissionStatus before = snapshot.rolePermissionStatus();
        if (before != RolePermissionStatus.ACTIVE) {
            return;
        }
        record(AuditAction.ROLE_PERMISSION_REVOKED, "ROLE_PERMISSION", snapshot.roleId() + ":" + snapshot.permissionId(),
                snapshot.roleName(), snapshot.roleKey(), snapshot.permissionId(),
                List.of(change("status", "ACTIVE", "REVOKED")));
    }

    private void record(AuditAction action, String aggregateType, String aggregateId, String roleName, String roleKey,
                        String permissionId,
                        List<FieldChange> changes) {
        auditService.recordAuthorizationChange(
                action, aggregateType, aggregateId, roleName, roleKey, permissionId, changes);
    }

    private FieldChange change(String field, Object before, Object after) {
        return fieldChangeMapper.toFieldChange(field, before, after);
    }

    @SuppressWarnings("unchecked")
    private List<UserRoleResponse> userRoleResponses(Object result) {
        return (List<UserRoleResponse>) result;
    }

    private static String stringArg(Object[] args, int index) {
        return (String) args[index];
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListArg(Object[] args, int index) {
        return (List<String>) args[index];
    }

    private record AuditSnapshot(String roleName, String roleKey, String roleId, String userSub, String permissionId,
                                 NexusRoleStatus roleStatus,
                                 NexusUserRoleStatus userRoleStatus, PermissionStatus permissionStatus,
                                 RolePermissionStatus rolePermissionStatus,
                                 Map<String, NexusUserRoleStatus> userRoleStatuses) {
        static AuditSnapshot empty() {
            return new AuditSnapshot(null, null, null, null, null, null, null, null, null, Map.of());
        }
    }

    private static final class AuditedInvocationException extends RuntimeException {
        private AuditedInvocationException(Throwable cause) {
            super(cause);
        }
    }
}
