package com.designpattern.cognitorbac.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleKeyTests {

    @Test
    void acceptsCanonicalCoarseRole() {
        assertEquals("deliveryops:developer", RoleKey.requireCanonical("deliveryops:developer"));
    }

    @Test
    void rejectsPermissionStyleGroup() {
        assertThrows(IllegalArgumentException.class,
                () -> RoleKey.requireCanonical("deliveryops:stores:write"));
    }

    @Test
    void rejectsNonCanonicalCase() {
        assertThrows(IllegalArgumentException.class,
                () -> RoleKey.requireCanonical("DeliveryOps:developer"));
    }
}
