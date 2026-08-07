package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.config.CognitoProperties;
import com.designpattern.cognitorbac.dto.CreateGroupRequest;
import com.designpattern.cognitorbac.dto.GroupResponse;
import com.designpattern.cognitorbac.dto.PagedResponse;
import com.designpattern.cognitorbac.dto.UpdateGroupRequest;
import com.designpattern.cognitorbac.dto.UserResponse;
import com.designpattern.cognitorbac.exception.CognitoIntegrationException;
import com.designpattern.cognitorbac.exception.ResourceConflictException;
import com.designpattern.cognitorbac.exception.ResourceNotFoundException;
import com.designpattern.cognitorbac.mapper.CognitoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UpdateGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.util.List;

/**
 * CRUD operations over Cognito groups plus group membership management.
 */
@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final CognitoIdentityProviderClient cognito;
    private final CognitoProperties properties;
    private final CognitoMapper mapper;

    public GroupService(CognitoIdentityProviderClient cognito,
                        CognitoProperties properties,
                        CognitoMapper mapper) {
        this.cognito = cognito;
        this.properties = properties;
        this.mapper = mapper;
    }

    public PagedResponse<GroupResponse> listGroups(Integer limit, String nextToken) {
        int pageSize = resolveLimit(limit);
        try {
            ListGroupsRequest.Builder builder = ListGroupsRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .limit(pageSize);
            if (nextToken != null && !nextToken.isBlank()) {
                builder.nextToken(nextToken);
            }
            ListGroupsResponse response = cognito.listGroups(builder.build());
            List<GroupResponse> groups = response.groups().stream()
                    .map(mapper::toGroupResponse)
                    .toList();
            return PagedResponse.of(groups, response.nextToken());
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("listGroups", ex);
        }
    }

    public GroupResponse getGroup(String groupName) {
        try {
            return mapper.toGroupResponse(cognito.getGroup(GetGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(groupName)
                    .build()).group());
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException ex) {
            throw ResourceNotFoundException.group(groupName);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("getGroup", ex);
        }
    }

    public GroupResponse createGroup(CreateGroupRequest request) {
        try {
            var builder = software.amazon.awssdk.services.cognitoidentityprovider.model.CreateGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(request.groupName());
            if (request.description() != null) {
                builder.description(request.description());
            }
            if (request.precedence() != null) {
                builder.precedence(request.precedence());
            }
            if (request.roleArn() != null && !request.roleArn().isBlank()) {
                builder.roleArn(request.roleArn());
            }
            CreateGroupResponse response = cognito.createGroup(builder.build());
            return mapper.toGroupResponse(response.group());
        } catch (GroupExistsException ex) {
            throw new ResourceConflictException("Group already exists: " + request.groupName());
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("createGroup", ex);
        }
    }

    public GroupResponse updateGroup(String groupName, UpdateGroupRequest request) {
        try {
            var builder = software.amazon.awssdk.services.cognitoidentityprovider.model.UpdateGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(groupName);
            if (request.description() != null) {
                builder.description(request.description());
            }
            if (request.precedence() != null) {
                builder.precedence(request.precedence());
            }
            if (request.roleArn() != null) {
                builder.roleArn(request.roleArn());
            }
            UpdateGroupResponse response = cognito.updateGroup(builder.build());
            return mapper.toGroupResponse(response.group());
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException ex) {
            throw ResourceNotFoundException.group(groupName);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("updateGroup", ex);
        }
    }

    public void addUserToGroup(String groupName, String username) {
        try {
            cognito.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(groupName)
                    .username(username)
                    .build());
        } catch (UserNotFoundException ex) {
            throw ResourceNotFoundException.user(username);
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException ex) {
            throw ResourceNotFoundException.group(groupName);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("adminAddUserToGroup", ex);
        }
    }

    public void removeUserFromGroup(String groupName, String username) {
        try {
            cognito.adminRemoveUserFromGroup(AdminRemoveUserFromGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(groupName)
                    .username(username)
                    .build());
        } catch (UserNotFoundException ex) {
            throw ResourceNotFoundException.user(username);
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException ex) {
            throw ResourceNotFoundException.group(groupName);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("adminRemoveUserFromGroup", ex);
        }
    }

    public PagedResponse<UserResponse> listUsersInGroup(String groupName, Integer limit, String nextToken) {
        int pageSize = resolveLimit(limit);
        try {
            ListUsersInGroupRequest.Builder builder = ListUsersInGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(groupName)
                    .limit(pageSize);
            if (nextToken != null && !nextToken.isBlank()) {
                builder.nextToken(nextToken);
            }
            ListUsersInGroupResponse response = cognito.listUsersInGroup(builder.build());
            List<UserResponse> users = response.users().stream()
                    .map(user -> mapper.toUserResponse(user, List.of()))
                    .toList();
            return PagedResponse.of(users, response.nextToken());
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException ex) {
            throw ResourceNotFoundException.group(groupName);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("listUsersInGroup", ex);
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return properties.getDefaultPageSize();
        }
        return Math.min(limit, 60);
    }

    private CognitoIntegrationException wrap(String operation, CognitoIdentityProviderException ex) {
        log.error("Cognito {} failed: {}", operation, ex.awsErrorDetails() != null
                ? ex.awsErrorDetails().errorMessage() : ex.getMessage());
        return new CognitoIntegrationException("Cognito operation failed: " + operation, ex);
    }
}
