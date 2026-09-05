package com.designpattern.cognitorbac.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditServiceTests {

    @Mock private AuditEntryRepository repository;
    @Mock private AuditEntryMapper mapper;

    @AfterEach
    void clearContext() {
        AuditContext.clear();
    }

    @Test
    void rejectsAuditWithoutUserIdentity() {
        AuditService service = new AuditService(repository, mapper, new AuditActionFilter());
        AuditContext.set(null, null, "role approved", "request-1");

        assertThrows(IllegalStateException.class, () -> service.recordAuthorizationChange(
                AuditAction.ROLE_CREATED, "ROLE", "role-1", "admin", "billing:admin", null, List.of()));

        verifyNoInteractions(repository, mapper);
    }

    @Test
    void rejectsBlankAuditReason() {
        AuditService service = new AuditService(repository, mapper, new AuditActionFilter());
        AuditContext.set("user-sub", "admin@nexus.example", " ", "request-1");

        assertThrows(IllegalArgumentException.class, () -> service.recordAuthorizationChange(
                AuditAction.ROLE_CREATED, "ROLE", "role-1", "admin", "billing:admin", null, List.of()));

        verifyNoInteractions(repository, mapper);
    }
}
