package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.config.CognitoProperties;
import com.designpattern.cognitorbac.dto.PagedResponse;
import com.designpattern.cognitorbac.dto.UserResponse;
import com.designpattern.cognitorbac.exception.CognitoIntegrationException;
import com.designpattern.cognitorbac.exception.ResourceNotFoundException;
import com.designpattern.cognitorbac.mapper.CognitoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.util.List;

/**
 * Read operations over Cognito users in the configured User Pool.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final CognitoIdentityProviderClient cognito;
    private final CognitoProperties properties;
    private final CognitoMapper mapper;

    public UserService(CognitoIdentityProviderClient cognito,
                       CognitoProperties properties,
                       CognitoMapper mapper) {
        this.cognito = cognito;
        this.properties = properties;
        this.mapper = mapper;
    }

    /**
     * Lists the application's users, i.e. the members of the Azure-managed source
     * group ({@code cognito.source-group}), with cursor pagination.
     *
     * <p>Cognito's {@code ListUsersInGroup} API does not support attribute
     * filtering, so no {@code filter} parameter is offered here (unlike a
     * pool-wide listing).</p>
     *
     * @param limit         page size; falls back to the configured default
     * @param nextToken     opaque token from a previous page (nullable)
     * @param includeGroups when true, resolves group memberships per user
     *                      (extra API call per user)
     */
    public PagedResponse<UserResponse> listUsers(Integer limit,
                                                 String nextToken,
                                                 boolean includeGroups) {
        int pageSize = resolveLimit(limit);
        String sourceGroup = properties.getSourceGroup();
        try {
            ListUsersInGroupRequest.Builder builder = ListUsersInGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(sourceGroup)
                    .limit(pageSize);
            if (nextToken != null && !nextToken.isBlank()) {
                builder.nextToken(nextToken);
            }

            ListUsersInGroupResponse response = cognito.listUsersInGroup(builder.build());
            List<UserResponse> users = response.users().stream()
                    .map(user -> mapper.toUserResponse(
                            user,
                            includeGroups ? groupsForUser(user.username()) : List.of()))
                    .toList();

            return PagedResponse.of(users, response.nextToken());
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException ex) {
            throw ResourceNotFoundException.group(sourceGroup);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("listUsersInGroup", ex);
        }
    }

    /**
     * Fetches a single user with full attribute detail and group memberships.
     */
    public UserResponse getUser(String username) {
        try {
            AdminGetUserResponse response = cognito.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .username(username)
                    .build());

            return mapper.toUserResponse(
                    response.username(),
                    response.userAttributes(),
                    Boolean.TRUE.equals(response.enabled()),
                    response.userStatusAsString(),
                    response.userCreateDate(),
                    response.userLastModifiedDate(),
                    groupsForUser(username));
        } catch (UserNotFoundException ex) {
            throw ResourceNotFoundException.user(username);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("adminGetUser", ex);
        }
    }

    /**
     * Resolves the group names a user belongs to.
     */
    public List<String> groupsForUser(String username) {
        try {
            return cognito.adminListGroupsForUser(AdminListGroupsForUserRequest.builder()
                            .userPoolId(properties.getUserPoolId())
                            .username(username)
                            .build())
                    .groups().stream()
                    .map(software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType::groupName)
                    .toList();
        } catch (UserNotFoundException ex) {
            throw ResourceNotFoundException.user(username);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("adminListGroupsForUser", ex);
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return properties.getDefaultPageSize();
        }
        // Cognito hard limit for ListUsersInGroup is 60.
        return Math.min(limit, 60);
    }

    private CognitoIntegrationException wrap(String operation, CognitoIdentityProviderException ex) {
        log.error("Cognito {} failed: {}", operation, ex.awsErrorDetails() != null
                ? ex.awsErrorDetails().errorMessage() : ex.getMessage());
        return new CognitoIntegrationException("Cognito operation failed: " + operation, ex);
    }
}
