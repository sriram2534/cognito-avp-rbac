package com.designpattern.cognitorbac.controller;

import com.designpattern.cognitorbac.dto.AddUsersToGroupRequest;
import com.designpattern.cognitorbac.dto.CreateGroupRequest;
import com.designpattern.cognitorbac.dto.GroupResponse;
import com.designpattern.cognitorbac.dto.PagedResponse;
import com.designpattern.cognitorbac.dto.UpdateGroupRequest;
import com.designpattern.cognitorbac.dto.UserResponse;
import com.designpattern.cognitorbac.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * (create/update group, add/remove members) require membership of the
 * configured admin group.</p>
 */
@RestController
@RequestMapping("/api/v1/groups")
@PreAuthorize("isAuthenticated()")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
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
    @PreAuthorize("hasRole(@rbac.adminRole)")
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        GroupResponse created = groupService.createGroup(request);
        URI location = uriBuilder.path("/api/v1/groups/{groupName}")
                .buildAndExpand(created.groupName())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{groupName}")
    @PreAuthorize("hasRole(@rbac.adminRole)")
    public ResponseEntity<GroupResponse> updateGroup(@PathVariable String groupName,
                                                     @Valid @RequestBody UpdateGroupRequest request) {
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
    @PreAuthorize("hasRole(@rbac.adminRole)")
    public ResponseEntity<Void> addUsersToGroup(@PathVariable String groupName,
                                                @Valid @RequestBody AddUsersToGroupRequest request) {
        request.usernames().forEach(username -> groupService.addUserToGroup(groupName, username));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/{groupName}/users/{username}")
    @PreAuthorize("hasRole(@rbac.adminRole)")
    public ResponseEntity<Void> removeUserFromGroup(@PathVariable String groupName,
                                                    @PathVariable String username) {
        groupService.removeUserFromGroup(groupName, username);
        return ResponseEntity.noContent().build();
    }
}
