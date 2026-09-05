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
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.util.List;

/** Cognito identity reads. Nexus role membership is read from MongoDB. */
@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final CognitoIdentityProviderClient cognito;
    private final CognitoProperties properties;
    private final CognitoMapper mapper;
    private final RoleService roleService;

    public UserService(CognitoIdentityProviderClient cognito, CognitoProperties properties,
                       CognitoMapper mapper, RoleService roleService) {
        this.cognito = cognito;
        this.properties = properties;
        this.mapper = mapper;
        this.roleService = roleService;
    }

    public PagedResponse<UserResponse> listUsers(Integer limit, String nextToken, boolean includeRoles) {
        try {
            ListUsersRequest.Builder builder = ListUsersRequest.builder().userPoolId(properties.getUserPoolId())
                    .limit(resolveLimit(limit));
            if (nextToken != null && !nextToken.isBlank()) builder.paginationToken(nextToken);
            ListUsersResponse response = cognito.listUsers(builder.build());
            List<UserResponse> users = response.users().stream()
                    .map(user -> mapper.toUserResponse(user, includeRoles
                            ? roleService.roleKeysForUser(mapper.subOf(user)) : List.of()))
                    .toList();
            return PagedResponse.of(users, response.paginationToken());
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("listUsers", ex);
        }
    }

    public UserResponse getUser(String username) {
        try {
            AdminGetUserResponse response = cognito.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(properties.getUserPoolId()).username(username).build());
            String userSub = mapper.subOf(response.userAttributes());
            return mapper.toUserResponse(response.username(), response.userAttributes(), Boolean.TRUE.equals(response.enabled()),
                    response.userStatusAsString(), response.userCreateDate(), response.userLastModifiedDate(),
                    roleService.roleKeysForUser(userSub));
        } catch (UserNotFoundException ex) {
            throw ResourceNotFoundException.user(username);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("adminGetUser", ex);
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) return properties.getDefaultPageSize();
        return Math.min(limit, 60);
    }

    private CognitoIntegrationException wrap(String operation, CognitoIdentityProviderException ex) {
        log.error("Cognito {} failed: {}", operation, ex.awsErrorDetails() != null
                ? ex.awsErrorDetails().errorMessage() : ex.getMessage());
        return new CognitoIntegrationException("Cognito operation failed: " + operation, ex);
    }
}
