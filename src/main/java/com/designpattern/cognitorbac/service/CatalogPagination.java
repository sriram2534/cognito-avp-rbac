package com.designpattern.cognitorbac.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Creates bounded, deterministic page requests from public API parameters. */
final class CatalogPagination {
    private static final int MAX_PAGE_SIZE = 100;

    private CatalogPagination() {
    }

    static Pageable create(int page, int size, String sortBy, String direction,
                           Map<String, List<String>> allowedSorts, String defaultSort) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String selectedSort = sortBy == null || sortBy.isBlank() ? defaultSort : sortBy.trim();
        List<String> properties = allowedSorts.get(selectedSort);
        if (properties == null) {
            throw new IllegalArgumentException("sortBy must be one of: " + String.join(", ", allowedSorts.keySet()));
        }
        Sort.Direction selectedDirection;
        try {
            selectedDirection = Sort.Direction.fromString(
                    direction == null ? "asc" : direction.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("direction must be 'asc' or 'desc'");
        }
        return PageRequest.of(page, size,
                Sort.by(selectedDirection, properties.toArray(String[]::new)));
    }
}
