package com.designpattern.cognitorbac.audit;

import com.designpattern.cognitorbac.dto.RoleResponse;
import com.designpattern.cognitorbac.permission.PermissionRepository;
import com.designpattern.cognitorbac.permission.RolePermissionRepository;
import com.designpattern.cognitorbac.role.NexusRole;
import com.designpattern.cognitorbac.role.NexusRoleRepository;
import com.designpattern.cognitorbac.role.NexusRoleStatus;
import com.designpattern.cognitorbac.role.NexusUserRoleRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationAuditAspectTests {

    @Mock private AuditService auditService;
    @Mock private FieldChangeMapper fieldChangeMapper;
    @Mock private NexusRoleRepository roles;
    @Mock private NexusUserRoleRepository userRoles;
    @Mock private PermissionRepository permissions;
    @Mock private RolePermissionRepository rolePermissions;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private AuthorizationAudit authorizationAudit;

    private AuthorizationAuditAspect aspect;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        lenient().when(fieldChangeMapper.toFieldChange(any(), any(), any()))
                .thenAnswer(invocation -> new FieldChange(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        aspect = new AuthorizationAuditAspect(auditService, fieldChangeMapper, roles, userRoles,
                permissions, rolePermissions, transactionManager);
    }

    @Test
    void commitsMutationAndAuditTogether() throws Throwable {
        RoleResponse role = activeRoleResponse();
        when(authorizationAudit.operation()).thenReturn(AuthorizationAuditOperation.ROLE_CREATED);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(role);

        aspect.auditSuccessfulMutation(joinPoint, authorizationAudit);

        InOrder order = inOrder(joinPoint, auditService, transactionManager);
        order.verify(joinPoint).proceed();
        order.verify(auditService).recordAuthorizationChange(eq(AuditAction.ROLE_CREATED), eq("ROLE"),
                eq("role-1"), eq("admin"), eq("billing:admin"), isNull(), anyList());
        order.verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void rollsBackMutationWhenAuditPersistenceFails() throws Throwable {
        RoleResponse role = activeRoleResponse();
        when(authorizationAudit.operation()).thenReturn(AuthorizationAuditOperation.ROLE_CREATED);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(role);
        doThrow(new DataAccessResourceFailureException("audit unavailable"))
                .when(auditService).recordAuthorizationChange(eq(AuditAction.ROLE_CREATED), eq("ROLE"),
                        eq("role-1"), eq("admin"), eq("billing:admin"), isNull(), anyList());

        assertThrows(DataAccessResourceFailureException.class,
                () -> aspect.auditSuccessfulMutation(joinPoint, authorizationAudit));

        InOrder order = inOrder(joinPoint, auditService, transactionManager);
        order.verify(joinPoint).proceed();
        order.verify(auditService).recordAuthorizationChange(eq(AuditAction.ROLE_CREATED), eq("ROLE"),
                eq("role-1"), eq("admin"), eq("billing:admin"), isNull(), anyList());
        order.verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void skipsAuditForIdempotentRoleActivation() throws Throwable {
        NexusRole activeRole = new NexusRole(
                "billing:admin", "billing", "admin", "Billing admin", null, "user-sub");
        when(authorizationAudit.operation()).thenReturn(AuthorizationAuditOperation.ROLE_ACTIVATED);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"role-1"});
        when(roles.findByRoleId("role-1")).thenReturn(Optional.of(activeRole));
        when(joinPoint.proceed()).thenReturn(activeRoleResponse());

        aspect.auditSuccessfulMutation(joinPoint, authorizationAudit);

        verifyNoInteractions(auditService);
    }

    private RoleResponse activeRoleResponse() {
        return new RoleResponse("role-1", "billing:admin", "billing", "admin", "Billing admin",
                null, NexusRoleStatus.ACTIVE, null, null, 0L);
    }
}
