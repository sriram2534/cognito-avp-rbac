package com.designpattern.cognitorbac.permission;

import java.util.Locale;
import java.util.regex.Pattern;

/** Canonical Cognito group name used as the application's role key. */
public final class RoleKey {
    private static final Pattern ROLE_KEY = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,62}:[a-z0-9][a-z0-9_-]{0,62}$");

    private RoleKey() {
    }

    public static String requireCanonical(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("roleKey is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!value.equals(normalized) || !ROLE_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "roleKey must be lowercase <module>:<role> using letters, numbers, '_' or '-'");
        }
        return normalized;
    }
}
