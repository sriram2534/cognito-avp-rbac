package com.designpattern.cognitorbac.role;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NexusRoleDomainTests {

    @Test
    void roleUsesUuidAndStartsActive() {
        NexusRole role = new NexusRole("deliveryops:developer", "deliveryops", "developer",
                "DeliveryOps Developer", null, "actor-sub");

        assertNotNull(role.getRoleId());
        assertEquals(NexusRoleStatus.ACTIVE, role.getStatus());
        assertEquals("deliveryops:developer", role.getRoleKey());
    }

    @Test
    void removedUserRoleRestoresWithoutNewRelationshipIdentity() {
        NexusUserRole relationship = new NexusUserRole("user-sub", "role-id", "actor-sub");
        relationship.remove("actor-sub");

        assertEquals(NexusUserRoleStatus.REMOVED, relationship.getStatus());
        assertNotNull(relationship.getRemovedAt());

        relationship.restore("actor-sub");

        assertEquals(NexusUserRoleStatus.ACTIVE, relationship.getStatus());
        assertNotNull(relationship.getAssignedAt());
        assertNull(relationship.getRemovedAt());
    }
}
