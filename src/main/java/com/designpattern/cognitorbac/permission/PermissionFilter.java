package com.designpattern.cognitorbac.permission;

import java.util.Locale;

/** Optional filters for the permission catalog. */
public record PermissionFilter(
        String module,
        String resourceType,
        String access,
        PermissionStatus status,
        String search
) {
    private static final int MAX_SEARCH_LENGTH = 100;

    public PermissionFilter {
        module = normalizeCoordinate(module);
        resourceType = normalizeCoordinate(resourceType);
        access = normalizeCoordinate(access);
        search = normalizeSearch(search);
    }

    private static String normalizeCoordinate(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("search must not exceed " + MAX_SEARCH_LENGTH + " characters");
        }
        return normalized;
    }
}
