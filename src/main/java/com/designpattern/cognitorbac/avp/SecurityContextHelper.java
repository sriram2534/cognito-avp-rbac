package com.designpattern.cognitorbac.avp;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Helper to extract caller information from the Spring Security context.
 * Provides access to the authenticated user's Cognito groups and raw token.
 */
@Component
public class SecurityContextHelper {

    private static final String GROUPS_CLAIM = "cognito:groups";
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Extracts the Cognito groups from the current authenticated user's JWT token.
     */
    public List<String> getCallerGroups() {
        Jwt jwt = getJwt();
        if (jwt == null) return List.of();

        Object claim = jwt.getClaim(GROUPS_CLAIM);
        if (claim instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Extracts the Cognito groups from Spring Security authorities (ROLE_ prefixed).
     */
    public List<String> getCallerGroupsFromAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();

        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()))
                .toList();
    }

    /**
     * Gets the raw ID token string from the current security context.
     */
    public String getIdentityToken() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getTokenValue() : null;
    }

    /**
     * Gets the authenticated user's subject (sub claim).
     */
    public String getCallerSub() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getSubject() : null;
    }

    /**
     * Gets the authenticated user's email from the JWT email claim.
     */
    public String getCallerEmail() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }

    private Jwt getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}
