package com.designpattern.cognitorbac.controller;

import com.designpattern.cognitorbac.dto.AddUsersToGroupRequest;
import com.designpattern.cognitorbac.dto.CreateGroupRequest;
import com.designpattern.cognitorbac.dto.GroupResponse;
import com.designpattern.cognitorbac.dto.PagedResponse;
import com.designpattern.cognitorbac.dto.UpdateGroupRequest;
import com.designpattern.cognitorbac.dto.UserResponse;
import com.designpattern.cognitorbac.audit.AuditContextFilter;
import com.designpattern.cognitorbac.service.GroupService;
import com.designpattern.cognitorbac.service.RolePermissionService;
import com.designpattern.cognitorbac.dto.PermissionResponse;
import com.designpattern.cognitorbac.dto.RolePermissionResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * REST API for managing Cognito groups (RBAC roles) and membership.
 *
 * <p>Cognito groups are coarse application roles. Their permissions are managed
 * separately in MongoDB; group lifecycle does not make authorization decisions.</p>
 */
@RestController
@RequestMapping("/api/v1/groups")
@PreAuthorize("isAuthenticated()")
public class GroupController {

    private final GroupService groupService;
    private final RolePermissionService rolePermissionService;

    public GroupController(GroupService groupService, RolePermissionService rolePermissionService) {
        this.groupService = groupService;
        this.rolePermissionService = rolePermissionService;
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
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason,
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
                                                     @Valid @RequestBody UpdateGroupRequest request,
                                                     @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        return ResponseEntity.ok(groupService.updateGroup(groupName, request));
    }

    @DeleteMapping("/{groupName}")
    @PreAuthorize("hasRole(@rbac.adminRole)")
    public ResponseEntity<Void> deleteGroup(@PathVariable String groupName,
                                            @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        groupService.deleteGroup(groupName);
        return ResponseEntity.noContent().build();
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
                                                @Valid @RequestBody AddUsersToGroupRequest request,
                                                @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        request.usernames().forEach(username -> groupService.addUserToGroup(groupName, username));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/{groupName}/users/{username}")
    @PreAuthorize("hasRole(@rbac.adminRole)")
    public ResponseEntity<Void> removeUserFromGroup(@PathVariable String groupName,
                                                    @PathVariable String username,
                                                    @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        groupService.removeUserFromGroup(groupName, username);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupName}/permissions/{permissionId}")
    @PreAuthorize("hasRole(@rbac.adminRole)")
    public ResponseEntity<RolePermissionResponse> grantPermission(@PathVariable String groupName,
                                                                   @PathVariable String permissionId,
                                                                   @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rolePermissionService.grant(groupName, permissionId));
    }

    @DeleteMapping("/{groupName}/permissions/{permissionId}")
    @PreAuthorize("hasRole(@rbac.adminRole)")
    public ResponseEntity<Void> revokePermission(@PathVariable String groupName,
                                                  @PathVariable String permissionId,
                                                  @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        rolePermissionService.revoke(groupName, permissionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupName}/permissions")
    public List<PermissionResponse> permissions(@PathVariable String groupName) {
        return rolePermissionService.permissionsForRole(groupName);
    }
}
