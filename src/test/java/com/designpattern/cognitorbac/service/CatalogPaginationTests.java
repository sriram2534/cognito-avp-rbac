package com.designpattern.cognitorbac.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogPaginationTests {
    private static final Map<String, List<String>> SORTS = Map.of(
            "name", List.of("name", "roleId"));

    @Test
    void createsBoundedDeterministicPageRequest() {
        var pageable = CatalogPagination.create(2, 25, "name", "DESC", SORTS, "name");

        assertEquals(2, pageable.getPageNumber());
        assertEquals(25, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("name").getDirection());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("roleId").getDirection());
    }

    @Test
    void rejectsInvalidPaginationAndSortInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> CatalogPagination.create(-1, 20, "name", "asc", SORTS, "name"));
        assertThrows(IllegalArgumentException.class,
                () -> CatalogPagination.create(0, 101, "name", "asc", SORTS, "name"));
        assertThrows(IllegalArgumentException.class,
                () -> CatalogPagination.create(0, 20, "_id", "asc", SORTS, "name"));
        assertThrows(IllegalArgumentException.class,
                () -> CatalogPagination.create(0, 20, "name", "sideways", SORTS, "name"));
    }
}
