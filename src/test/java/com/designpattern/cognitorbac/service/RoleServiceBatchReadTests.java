package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.mapper.RoleMapper;
import com.designpattern.cognitorbac.role.NexusRole;
import com.designpattern.cognitorbac.role.NexusRoleRepository;
import com.designpattern.cognitorbac.role.NexusRoleStatus;
import com.designpattern.cognitorbac.role.NexusUserRole;
import com.designpattern.cognitorbac.role.NexusUserRoleRepository;
import com.designpattern.cognitorbac.role.NexusUserRoleStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceBatchReadTests {
    @Mock private NexusRoleRepository roles;
    @Mock private NexusUserRoleRepository userRoles;
    @Mock private RoleMapper mapper;

    @Test
    void resolvesManyUsersWithOneMembershipQueryAndOneRoleQuery() {
        NexusUserRole userOneRole = membership("user-1", "role-1");
        NexusUserRole userTwoRole = membership("user-2", "role-2");
        NexusRole activeRole = role("role-1", "billing:admin", NexusRoleStatus.ACTIVE);
        NexusRole inactiveRole = role("role-2", "billing:viewer", NexusRoleStatus.INACTIVE);
        when(userRoles.findByUserSubInAndStatus(anyCollection(), org.mockito.ArgumentMatchers.eq(NexusUserRoleStatus.ACTIVE)))
                .thenReturn(List.of(userOneRole, userTwoRole));
        when(roles.findByRoleIdIn(anyCollection())).thenReturn(List.of(activeRole, inactiveRole));
        RoleService service = new RoleService(roles, userRoles, mapper);

        Map<String, List<String>> result = service.roleKeysForUsers(List.of("user-1", "user-2"));

        assertEquals(List.of("billing:admin"), result.get("user-1"));
        assertEquals(List.of(), result.get("user-2"));
        verify(userRoles).findByUserSubInAndStatus(anyCollection(),
                org.mockito.ArgumentMatchers.eq(NexusUserRoleStatus.ACTIVE));
        verify(roles).findByRoleIdIn(anyCollection());
    }

    private NexusUserRole membership(String userSub, String roleId) {
        NexusUserRole membership = mock(NexusUserRole.class);
        lenient().when(membership.getUserSub()).thenReturn(userSub);
        when(membership.getRoleId()).thenReturn(roleId);
        return membership;
    }

    private NexusRole role(String roleId, String roleKey, NexusRoleStatus status) {
        NexusRole role = mock(NexusRole.class);
        lenient().when(role.getRoleId()).thenReturn(roleId);
        lenient().when(role.getRoleKey()).thenReturn(roleKey);
        when(role.getStatus()).thenReturn(status);
        return role;
    }
}
