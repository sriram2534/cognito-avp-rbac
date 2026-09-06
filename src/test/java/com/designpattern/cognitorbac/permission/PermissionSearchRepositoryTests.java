package com.designpattern.cognitorbac.permission;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionSearchRepositoryTests {
    @Test
    void combinesCoordinateStatusAndEscapedSearchFiltersBeforePaging() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        Permission permission = mock(Permission.class);
        when(mongoTemplate.count(any(Query.class), eq(Permission.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Permission.class))).thenReturn(List.of(permission));
        var repository = new PermissionSearchRepositoryImpl(mongoTemplate);

        var result = repository.search(new PermissionFilter(
                        " Billing ", " Invoices ", " READ ", PermissionStatus.ACTIVE, "invoice.*"),
                PageRequest.of(0, 25, Sort.by("module", "resourceType", "access", "permissionId")));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Permission.class));
        Query query = queryCaptor.getValue();
        String filter = query.getQueryObject().toString();
        assertTrue(filter.contains("billing"));
        assertTrue(filter.contains("invoices"));
        assertTrue(filter.contains("read"));
        assertTrue(filter.contains("ACTIVE"));
        assertTrue(filter.contains("\\Qinvoice.*\\E"));
        assertEquals(25, query.getLimit());
        assertEquals(1, result.getTotalElements());
    }
}
