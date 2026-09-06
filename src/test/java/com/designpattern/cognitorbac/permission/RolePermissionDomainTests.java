package com.designpattern.cognitorbac.permission;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RolePermissionDomainTests {

    @Test
    void grantRevokeAndRestoreMaintainValidityWindow() {
        RolePermission relationship = new RolePermission("role-id", "permission-id", "actor-sub");
        Instant firstGrant = relationship.getValidFrom();

        assertEquals(RolePermissionStatus.ACTIVE, relationship.getStatus());
        assertNotNull(firstGrant);
        assertNull(relationship.getValidUntil());

        relationship.revoke("actor-sub");

        assertEquals(RolePermissionStatus.REVOKED, relationship.getStatus());
        assertNotNull(relationship.getValidUntil());

        relationship.restore("actor-sub");

        assertEquals(RolePermissionStatus.ACTIVE, relationship.getStatus());
        assertFalse(relationship.getValidFrom().isBefore(firstGrant));
        assertNull(relationship.getValidUntil());
    }
}
