package com.designpattern.cognitorbac.controller;

import com.designpattern.cognitorbac.audit.AuditContextFilter;
import com.designpattern.cognitorbac.dto.AddUsersToRoleRequest;
import com.designpattern.cognitorbac.dto.CreateRoleRequest;
import com.designpattern.cognitorbac.dto.PermissionResponse;
import com.designpattern.cognitorbac.dto.RolePermissionResponse;
import com.designpattern.cognitorbac.dto.RoleResponse;
import com.designpattern.cognitorbac.dto.UpdateRoleRequest;
import com.designpattern.cognitorbac.dto.UserRoleResponse;
import com.designpattern.cognitorbac.service.RolePermissionService;
import com.designpattern.cognitorbac.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Database-owned Nexus roles, memberships, and permission assignments. */
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("isAuthenticated()")
public class RoleController {

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;

    public RoleController(RoleService roleService, RolePermissionService rolePermissionService) {
        this.roleService = roleService;
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return roleService.list();
    }

    @GetMapping("/{roleId}")
    public RoleResponse get(@PathVariable String roleId) {
        return roleService.get(roleId);
    }

    @PostMapping
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request,
                                               @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason,
                                               UriComponentsBuilder uriBuilder) {
        RoleResponse created = roleService.create(request);
        URI location = uriBuilder.path("/api/v1/roles/{roleId}").buildAndExpand(created.roleId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{roleId}")
    public RoleResponse update(@PathVariable String roleId, @Valid @RequestBody UpdateRoleRequest request,
                               @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        return roleService.update(roleId, request);
    }

    @PostMapping("/{roleId}/activate")
    public RoleResponse activate(@PathVariable String roleId,
                                 @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        return roleService.activate(roleId);
    }

    @PostMapping("/{roleId}/deactivate")
    public RoleResponse deactivate(@PathVariable String roleId,
                                   @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        return roleService.deactivate(roleId);
    }

    @GetMapping("/{roleId}/users")
    public List<UserRoleResponse> users(@PathVariable String roleId) {
        return roleService.users(roleId);
    }

    @PostMapping("/{roleId}/users")
    public ResponseEntity<List<UserRoleResponse>> assignUsers(@PathVariable String roleId,
                                                               @Valid @RequestBody AddUsersToRoleRequest request,
                                                               @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.assignUsers(roleId, request.userSubs()));
    }

    @DeleteMapping("/{roleId}/users/{userSub}")
    public ResponseEntity<Void> removeUser(@PathVariable String roleId, @PathVariable String userSub,
                                           @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        roleService.removeUser(roleId, userSub);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{roleId}/permissions")
    public List<PermissionResponse> permissions(@PathVariable String roleId) {
        return rolePermissionService.permissionsForRole(roleId);
    }

    @PostMapping("/{roleId}/permissions/{permissionId}")
    public ResponseEntity<RolePermissionResponse> grantPermission(@PathVariable String roleId,
                                                                   @PathVariable String permissionId,
                                                                   @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rolePermissionService.grant(roleId, permissionId));
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    public ResponseEntity<Void> revokePermission(@PathVariable String roleId, @PathVariable String permissionId,
                                                  @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        rolePermissionService.revoke(roleId, permissionId);
        return ResponseEntity.noContent().build();
    }
}
