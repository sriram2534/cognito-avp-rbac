package com.designpattern.cognitorbac.service;

import com.designpattern.cognitorbac.config.CognitoProperties;
import com.designpattern.cognitorbac.dto.CreateGroupRequest;
import com.designpattern.cognitorbac.dto.CreateGroupWithPolicyResponse;
import com.designpattern.cognitorbac.dto.avp.PolicyResponse;
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
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeleteGroupRequest;
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
    private final PolicyService policyService;

    public GroupService(CognitoIdentityProviderClient cognito,
                        CognitoProperties properties,
                        CognitoMapper mapper,
                        PolicyService policyService) {
        this.cognito = cognito;
        this.properties = properties;
        this.mapper = mapper;
        this.policyService = policyService;
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

    /**
     * Creates a Cognito group and its corresponding AVP policy atomically.
     *
     * <p>Authorization rules enforced upstream by AVP via
     * {@code AuthorizationService.canManageGroup()}:
     * <ul>
     *   <li>{@code global:global:admin} — can create any group (super admin, module admin,
     *       or resource-level)</li>
     *   <li>{@code module:global:admin} — can only create resource-level groups within
     *       their own module (e.g. {@code ops:global:admin} can create {@code ops:store:write})</li>
     *   <li>All others — denied</li>
     * </ul>
     *
     * <p>If AVP policy creation fails after the Cognito group is created, the group is
     * rolled back (deleted) to prevent an orphaned group with no access policy.</p>
     *
     * @param request the group creation request
     * @return the created group and its AVP policy
     */
    public CreateGroupWithPolicyResponse createGroupWithPolicy(CreateGroupRequest request) {
        GroupResponse group = createGroup(request);

        try {
            PolicyResponse policy = policyService.createPolicyForGroup(request.groupName());
            log.info("Created group [{}] with policy [{}]", request.groupName(), policy.policyId());
            return new CreateGroupWithPolicyResponse(group, policy);
        } catch (Exception ex) {
            log.error("AVP policy creation failed for group [{}], rolling back Cognito group",
                    request.groupName(), ex);
            rollbackGroup(request.groupName());
            throw ex;
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

    private void rollbackGroup(String groupName) {
        try {
            cognito.deleteGroup(DeleteGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(groupName)
                    .build());
            log.info("Rolled back Cognito group [{}] after AVP policy failure", groupName);
        } catch (Exception rollbackEx) {
            log.error("Failed to roll back Cognito group [{}] — manual cleanup required",
                    groupName, rollbackEx);
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
