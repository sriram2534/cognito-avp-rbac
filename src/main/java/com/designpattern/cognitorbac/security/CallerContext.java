package com.designpattern.cognitorbac.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** Extracts authenticated caller details needed by audit and management flows. */
@Component
public class CallerContext {

    private static final String GROUPS_CLAIM = "cognito:groups";

    public List<String> getCallerGroups() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            return List.of();
        }

        Object claim = jwt.getClaim(GROUPS_CLAIM);
        if (claim instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public String getCallerSub() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getSubject() : null;
    }

    private Jwt getJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}
