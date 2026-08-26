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
import com.designpattern.cognitorbac.outbox.AuthorizationOutboxService;
import com.designpattern.cognitorbac.permission.RoleKey;
import com.designpattern.cognitorbac.permission.RolePermissionRepository;
import com.designpattern.cognitorbac.permission.RolePermissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeleteGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UpdateGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * CRUD operations over Cognito groups plus group membership management.
 */
@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final CognitoIdentityProviderClient cognito;
    private final CognitoProperties properties;
    private final CognitoMapper mapper;
    private final UserService userService;
    private final AuthorizationOutboxService outboxService;
    private final RolePermissionRepository rolePermissions;
    public GroupService(CognitoIdentityProviderClient cognito,
                        CognitoProperties properties,
                        CognitoMapper mapper,
                        UserService userService,
                        AuthorizationOutboxService outboxService,
                        RolePermissionRepository rolePermissions) {
        this.cognito = cognito;
        this.properties = properties;
        this.mapper = mapper;
        this.userService = userService;
        this.outboxService = outboxService;
        this.rolePermissions = rolePermissions;
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
        String roleKey = RoleKey.requireCanonical(request.groupName());
        try {
            var builder = software.amazon.awssdk.services.cognitoidentityprovider.model.CreateGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(roleKey);
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
            GroupResponse group = mapper.toGroupResponse(response.group());
            log.info("Cognito role created without AVP policy mutation [roleKey={}]", group.groupName());
            return group;
        } catch (GroupExistsException ex) {
            throw new ResourceConflictException("Group already exists: " + roleKey);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("createGroup", ex);
        }
    }

    public GroupResponse updateGroup(String groupName, UpdateGroupRequest request) {
        groupName = RoleKey.requireCanonical(groupName);
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
            GroupResponse group = mapper.toGroupResponse(response.group());
            log.info("Cognito role updated without AVP policy mutation [roleKey={}]", group.groupName());
            return group;
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException ex) {
            throw ResourceNotFoundException.group(groupName);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("updateGroup", ex);
        }
    }

    public void addUserToGroup(String groupName, String username) {
        groupName = RoleKey.requireCanonical(groupName);
        String userSub = userService.getUser(username).sub();
        try {
            cognito.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(groupName)
                    .username(username)
                    .build());
            publishMembershipChanged(userSub, groupName, "ADDED");
            log.info("User added to Cognito role [roleKey={}] [userSub={}]", groupName, userSub);
        } catch (UserNotFoundException ex) {
            throw ResourceNotFoundException.user(username);
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException ex) {
            throw ResourceNotFoundException.group(groupName);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("adminAddUserToGroup", ex);
        }
    }

    public void removeUserFromGroup(String groupName, String username) {
        groupName = RoleKey.requireCanonical(groupName);
        String userSub = userService.getUser(username).sub();
        try {
            cognito.adminRemoveUserFromGroup(AdminRemoveUserFromGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(groupName)
                    .username(username)
                    .build());
            publishMembershipChanged(userSub, groupName, "REMOVED");
            log.info("User removed from Cognito role [roleKey={}] [userSub={}]", groupName, userSub);
        } catch (UserNotFoundException ex) {
            throw ResourceNotFoundException.user(username);
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException ex) {
            throw ResourceNotFoundException.group(groupName);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("adminRemoveUserFromGroup", ex);
        }
    }

    public PagedResponse<UserResponse> listUsersInGroup(String groupName, Integer limit, String nextToken) {
        groupName = RoleKey.requireCanonical(groupName);
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

    /** Ensures that a role assignment cannot reference a nonexistent Cognito group. */
    public GroupResponse requireRole(String roleKey) {
        return getGroup(RoleKey.requireCanonical(roleKey));
    }

    /**
     * A Cognito role may only be removed once its active permission assignments
     * have been explicitly revoked. This avoids a Cognito/Mongo split-brain
     * without pretending the two systems participate in one transaction.
     */
    public void deleteGroup(String roleKey) {
        roleKey = RoleKey.requireCanonical(roleKey);
        if (rolePermissions.countByRoleKeyAndStatus(roleKey, RolePermissionStatus.ACTIVE) > 0) {
            throw new ResourceConflictException(
                    "Role has active permissions; revoke them before deleting the Cognito group: " + roleKey);
        }
        try {
            cognito.deleteGroup(DeleteGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(roleKey)
                    .build());
            log.info("Cognito role deleted without AVP policy mutation [roleKey={}]", roleKey);
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException ex) {
            throw ResourceNotFoundException.group(roleKey);
        } catch (CognitoIdentityProviderException ex) {
            throw wrap("deleteGroup", ex);
        }
    }

    private void publishMembershipChanged(String sub, String roleKey, String operation) {
        outboxService.enqueue("USER_GROUP_MEMBERSHIP_CHANGED", sub, Map.of(
                "eventVersion", 1,
                "eventType", "USER_GROUP_MEMBERSHIP_CHANGED",
                "sub", sub,
                "roleKey", roleKey,
                "operation", operation,
                "correlationId", PermissionService.correlationId(),
                "occurredAt", Instant.now().toString()));
        log.debug("User-role membership invalidation prepared [userSub={}] [roleKey={}] [operation={}]",
                sub, roleKey, operation);
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
