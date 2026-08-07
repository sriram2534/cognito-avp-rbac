package com.designpattern.cognitorbac.controller;

import com.designpattern.cognitorbac.dto.AddUsersToGroupRequest;
import com.designpattern.cognitorbac.dto.CreateGroupRequest;
import com.designpattern.cognitorbac.dto.GroupResponse;
import com.designpattern.cognitorbac.dto.PagedResponse;
import com.designpattern.cognitorbac.dto.UpdateGroupRequest;
import com.designpattern.cognitorbac.dto.UserResponse;
import com.designpattern.cognitorbac.avp.SecurityContextHelper;
import com.designpattern.cognitorbac.service.AuthorizationService;
import com.designpattern.cognitorbac.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * REST API for managing Cognito groups (RBAC roles) and membership.
 *
 * <p>Read operations require any authenticated caller; write operations
 * (create/update group, add/remove members) are authorized by AVP, which
 * derives the caller's groups from the identity token and evaluates the
 * Cedar policies for the target group.</p>
 */
@RestController
@RequestMapping("/api/v1/groups")
@PreAuthorize("isAuthenticated()")
public class GroupController {

    private final GroupService groupService;
    private final AuthorizationService authorizationService;
    private final SecurityContextHelper securityContextHelper;

    public GroupController(GroupService groupService,
                           AuthorizationService authorizationService,
                           SecurityContextHelper securityContextHelper) {
        this.groupService = groupService;
        this.authorizationService = authorizationService;
        this.securityContextHelper = securityContextHelper;
    }

    @GetMapping
    public PagedResponse<GroupResponse> listGroups(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String nextToken) {
        return groupService.listGroups(limit, nextToken);
    }

    @GetMapping("/{groupName}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable String groupName) {
        return ResponseEntity.ok(groupService.getGroup(groupName));
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        authorizeGroupManagement(request.groupName());
        GroupResponse created = groupService.createGroup(request);
        URI location = uriBuilder.path("/api/v1/groups/{groupName}")
                .buildAndExpand(created.groupName())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{groupName}")
    public ResponseEntity<GroupResponse> updateGroup(@PathVariable String groupName,
                                                     @Valid @RequestBody UpdateGroupRequest request) {
        authorizeGroupManagement(groupName);
        return ResponseEntity.ok(groupService.updateGroup(groupName, request));
    }

    @GetMapping("/{groupName}/users")
    public PagedResponse<UserResponse> listUsersInGroup(
            @PathVariable String groupName,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String nextToken) {
        return groupService.listUsersInGroup(groupName, limit, nextToken);
    }

    @PostMapping("/{groupName}/users")
    public ResponseEntity<Void> addUsersToGroup(@PathVariable String groupName,
                                                @Valid @RequestBody AddUsersToGroupRequest request) {
        authorizeGroupManagement(groupName);
        request.usernames().forEach(username -> groupService.addUserToGroup(groupName, username));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/{groupName}/users/{username}")
    public ResponseEntity<Void> removeUserFromGroup(@PathVariable String groupName,
                                                    @PathVariable String username) {
        authorizeGroupManagement(groupName);
        groupService.removeUserFromGroup(groupName, username);
        return ResponseEntity.noContent().build();
    }

    /**
     * Authorizes a group-management operation via AVP. AVP derives the caller's
     * group membership from the identity token and evaluates the Cedar policies:
     * module-admin groups are restricted to super admins, resource groups to the
     * owning module admin.
     *
     * @throws AccessDeniedException if AVP returns DENY
     */
    private void authorizeGroupManagement(String groupName) {
        String identityToken = securityContextHelper.getIdentityToken();
        if (identityToken == null || !authorizationService.canManageGroup(identityToken, groupName)) {
            throw new AccessDeniedException("Not authorized to manage group: " + groupName);
        }
    }
}
