package com.designpattern.cognitorbac.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditActionFilterTests {

    private final AuditActionFilter filter = new AuditActionFilter();

    @Test
    void capturesRequiredRolePermissionAndMembershipActions() {
        assertTrue(filter.shouldCapture(AuditAction.ROLE_CREATED));
        assertTrue(filter.shouldCapture(AuditAction.ROLE_DEACTIVATED));
        assertTrue(filter.shouldCapture(AuditAction.USER_ROLE_ASSIGNED));
        assertTrue(filter.shouldCapture(AuditAction.PERMISSION_CREATED));
        assertTrue(filter.shouldCapture(AuditAction.PERMISSION_ACTIVATED));
        assertTrue(filter.shouldCapture(AuditAction.ROLE_PERMISSION_GRANTED));
    }

    @Test
    void filtersUnlistedMetadataActions() {
        assertFalse(filter.shouldCapture(AuditAction.ROLE_UPDATED));
        assertFalse(filter.shouldCapture(AuditAction.PERMISSION_DESCRIPTION_UPDATED));
    }
}
