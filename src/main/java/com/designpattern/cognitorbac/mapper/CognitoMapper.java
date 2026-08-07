package com.designpattern.cognitorbac.mapper;

import com.designpattern.cognitorbac.dto.GroupResponse;
import com.designpattern.cognitorbac.dto.UserResponse;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps AWS Cognito SDK model types into the service's public DTOs.
 */
@Component
public class CognitoMapper {

    private static final String ATTR_SUB = "sub";
    private static final String ATTR_EMAIL = "email";
    private static final String ATTR_EMAIL_VERIFIED = "email_verified";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_GIVEN_NAME = "given_name";
    private static final String ATTR_FAMILY_NAME = "family_name";

    /**
     * Maps a {@link UserType} (from listUsers) plus resolved group names.
     */
    public UserResponse toUserResponse(UserType user, List<String> groups) {
        Map<String, String> attrs = toAttributeMap(user.attributes());
        return new UserResponse(
                user.username(),
                attrs.get(ATTR_SUB),
                attrs.get(ATTR_EMAIL),
                Boolean.parseBoolean(attrs.get(ATTR_EMAIL_VERIFIED)),
                attrs.get(ATTR_NAME),
                attrs.get(ATTR_GIVEN_NAME),
                attrs.get(ATTR_FAMILY_NAME),
                Boolean.TRUE.equals(user.enabled()),
                user.userStatusAsString(),
                groups == null ? List.of() : groups,
                user.userCreateDate(),
                user.userLastModifiedDate());
    }

    /**
     * Maps an admin-get-user style attribute list (used when the SDK response is
     * not a {@link UserType}).
     */
    public UserResponse toUserResponse(String username,
                                       List<AttributeType> attributes,
                                       boolean enabled,
                                       String status,
                                       java.time.Instant createdAt,
                                       java.time.Instant lastModifiedAt,
                                       List<String> groups) {
        Map<String, String> attrs = toAttributeMap(attributes);
        return new UserResponse(
                username,
                attrs.get(ATTR_SUB),
                attrs.get(ATTR_EMAIL),
                Boolean.parseBoolean(attrs.get(ATTR_EMAIL_VERIFIED)),
                attrs.get(ATTR_NAME),
                attrs.get(ATTR_GIVEN_NAME),
                attrs.get(ATTR_FAMILY_NAME),
                enabled,
                status,
                groups == null ? List.of() : groups,
                createdAt,
                lastModifiedAt);
    }

    public GroupResponse toGroupResponse(GroupType group) {
        return new GroupResponse(
                group.groupName(),
                group.description(),
                group.precedence(),
                group.roleArn(),
                group.creationDate(),
                group.lastModifiedDate());
    }

    private Map<String, String> toAttributeMap(List<AttributeType> attributes) {
        if (attributes == null) {
            return Map.of();
        }
        return attributes.stream()
                .filter(a -> a.name() != null && a.value() != null)
                .collect(Collectors.toMap(
                        AttributeType::name,
                        AttributeType::value,
                        (a, b) -> a));
    }
}
