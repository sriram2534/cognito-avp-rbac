package com.designpattern.cognitorbac.mapper;

import com.designpattern.cognitorbac.dto.UserResponse;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Maps Cognito identity data; Nexus roles are supplied by the caller. */
@Component
public class CognitoMapper {
    private static final String ATTR_SUB = "sub";
    private static final String ATTR_EMAIL = "email";
    private static final String ATTR_EMAIL_VERIFIED = "email_verified";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_GIVEN_NAME = "given_name";
    private static final String ATTR_FAMILY_NAME = "family_name";

    public UserResponse toUserResponse(UserType user, List<String> roles) {
        Map<String, String> attrs = toAttributeMap(user.attributes());
        return new UserResponse(user.username(), attrs.get(ATTR_SUB), attrs.get(ATTR_EMAIL),
                Boolean.parseBoolean(attrs.get(ATTR_EMAIL_VERIFIED)), attrs.get(ATTR_NAME),
                attrs.get(ATTR_GIVEN_NAME), attrs.get(ATTR_FAMILY_NAME), Boolean.TRUE.equals(user.enabled()),
                user.userStatusAsString(), roles == null ? List.of() : roles,
                user.userCreateDate(), user.userLastModifiedDate());
    }

    public UserResponse toUserResponse(String username, List<AttributeType> attributes, boolean enabled,
                                       String status, java.time.Instant createdAt, java.time.Instant lastModifiedAt,
                                       List<String> roles) {
        Map<String, String> attrs = toAttributeMap(attributes);
        return new UserResponse(username, attrs.get(ATTR_SUB), attrs.get(ATTR_EMAIL),
                Boolean.parseBoolean(attrs.get(ATTR_EMAIL_VERIFIED)), attrs.get(ATTR_NAME),
                attrs.get(ATTR_GIVEN_NAME), attrs.get(ATTR_FAMILY_NAME), enabled, status,
                roles == null ? List.of() : roles, createdAt, lastModifiedAt);
    }

    public String subOf(UserType user) {
        return toAttributeMap(user.attributes()).get(ATTR_SUB);
    }

    public String subOf(List<AttributeType> attributes) {
        return toAttributeMap(attributes).get(ATTR_SUB);
    }

    private Map<String, String> toAttributeMap(List<AttributeType> attributes) {
        if (attributes == null) return Map.of();
        return attributes.stream().filter(a -> a.name() != null && a.value() != null)
                .collect(Collectors.toMap(AttributeType::name, AttributeType::value, (a, b) -> a));
    }
}
