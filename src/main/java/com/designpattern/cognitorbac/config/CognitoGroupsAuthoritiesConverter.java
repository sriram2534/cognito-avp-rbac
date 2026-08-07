package com.designpattern.cognitorbac.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts a Cognito-issued JWT into Spring Security authorities.
 *
 * <p>Cognito places group memberships in the {@code cognito:groups} claim and
 * OAuth2 scopes in the {@code scope} claim. Groups are mapped to
 * {@code ROLE_<group>} authorities (so {@code hasRole(...)} works), and scopes
 * are mapped to {@code SCOPE_<scope>} authorities.</p>
 */
public class CognitoGroupsAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String GROUPS_CLAIM = "cognito:groups";
    private static final String SCOPE_CLAIM = "scope";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Stream<String> groupAuthorities = extractGroups(jwt).stream()
                .map(group -> ROLE_PREFIX + group);

        Stream<String> scopeAuthorities = extractScopes(jwt).stream()
                .map(scope -> SCOPE_PREFIX + scope);

        return Stream.concat(groupAuthorities, scopeAuthorities)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableList());
    }

    private List<String> extractGroups(Jwt jwt) {
        Object claim = jwt.getClaim(GROUPS_CLAIM);
        if (claim instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private List<String> extractScopes(Jwt jwt) {
        Object claim = jwt.getClaim(SCOPE_CLAIM);
        if (claim instanceof String scopeString && !scopeString.isBlank()) {
            return List.of(scopeString.trim().split("\\s+"));
        }
        if (claim instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
