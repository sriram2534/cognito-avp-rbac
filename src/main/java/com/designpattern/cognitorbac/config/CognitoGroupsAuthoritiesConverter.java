package com.designpattern.cognitorbac.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.List;

/**
 * Converts a Cognito-issued JWT into Spring Security authorities.
 *
 * <p>Cognito places group memberships in the {@code cognito:groups} claim and
 * OAuth2 group memberships are mapped to {@code ROLE_<group>} authorities so
 * that the management API can protect its administrative operations.</p>
 */
public class CognitoGroupsAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String GROUPS_CLAIM = "cognito:groups";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return extractGroups(jwt).stream()
                .map(group -> ROLE_PREFIX + group)
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

}
