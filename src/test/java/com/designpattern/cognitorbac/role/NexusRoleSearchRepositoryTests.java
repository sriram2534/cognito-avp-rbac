package com.designpattern.cognitorbac.role;

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

class NexusRoleSearchRepositoryTests {
    @Test
    void appliesNormalizedFiltersEscapedSearchAndPaging() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        NexusRole role = mock(NexusRole.class);
        when(mongoTemplate.count(any(Query.class), eq(NexusRole.class))).thenReturn(21L);
        when(mongoTemplate.find(any(Query.class), eq(NexusRole.class))).thenReturn(List.of(role));
        var repository = new NexusRoleSearchRepositoryImpl(mongoTemplate);

        var result = repository.search(new RoleFilter(" Billing ", NexusRoleStatus.ACTIVE, "admin.*"),
                PageRequest.of(1, 10, Sort.by("module", "name", "roleId")));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(NexusRole.class));
        Query query = queryCaptor.getValue();
        String filter = query.getQueryObject().toString();
        assertTrue(filter.contains("billing"));
        assertTrue(filter.contains("ACTIVE"));
        assertTrue(filter.contains("\\Qadmin.*\\E"));
        assertEquals(10, query.getSkip());
        assertEquals(10, query.getLimit());
        assertEquals(21, result.getTotalElements());
    }
}
