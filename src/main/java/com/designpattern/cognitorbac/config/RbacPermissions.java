package com.designpattern.cognitorbac.config;

import org.springframework.stereotype.Component;

/**
 * Small helper bean referenced from {@code @PreAuthorize} SpEL expressions so
 * the admin Cognito group name stays configuration-driven rather than
 * hard-coded in annotations.
 *
 * <p>Usage: {@code @PreAuthorize("hasRole(@rbac.adminRole)")}. Spring Security's
 * {@code hasRole} automatically prepends the {@code ROLE_} prefix, matching the
 * authorities produced by {@link CognitoGroupsAuthoritiesConverter}.</p>
 */
@Component("rbac")
public class RbacPermissions {

    private final CognitoProperties properties;

    public RbacPermissions(CognitoProperties properties) {
        this.properties = properties;
    }

    public String getAdminRole() {
        return properties.getAdminGroup();
    }
}
