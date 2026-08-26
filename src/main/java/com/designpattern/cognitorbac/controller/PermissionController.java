package com.designpattern.cognitorbac.controller;

import com.designpattern.cognitorbac.audit.AuditContextFilter;
import com.designpattern.cognitorbac.dto.CreatePermissionRequest;
import com.designpattern.cognitorbac.dto.PermissionResponse;
import com.designpattern.cognitorbac.dto.RolePermissionResponse;
import com.designpattern.cognitorbac.dto.UpdatePermissionRequest;
import com.designpattern.cognitorbac.service.PermissionService;
import com.designpattern.cognitorbac.service.RolePermissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Administrative API for reusable permission source data. */
@RestController
@RequestMapping("/api/v1/permissions")
@PreAuthorize("hasRole(@rbac.adminRole)")
public class PermissionController {
    private final PermissionService permissionService;
    private final RolePermissionService rolePermissionService;

    public PermissionController(PermissionService permissionService, RolePermissionService rolePermissionService) {
        this.permissionService = permissionService;
        this.rolePermissionService = rolePermissionService;
    }

    @PostMapping
    public ResponseEntity<PermissionResponse> create(@Valid @RequestBody CreatePermissionRequest request,
                                                      @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason,
                                                      UriComponentsBuilder uriBuilder) {
        PermissionResponse created = permissionService.create(request);
        URI location = uriBuilder.path("/api/v1/permissions/{permissionId}")
                .buildAndExpand(created.permissionId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<PermissionResponse> list() {
        return permissionService.list();
    }

    @GetMapping("/{permissionId}")
    public PermissionResponse get(@PathVariable String permissionId) {
        return permissionService.get(permissionId);
    }

    @PatchMapping("/{permissionId}")
    public PermissionResponse updateDescription(@PathVariable String permissionId,
                                                @Valid @RequestBody UpdatePermissionRequest request,
                                                @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        return permissionService.updateDescription(permissionId, request);
    }

    @PostMapping("/{permissionId}/deactivate")
    public PermissionResponse deactivate(@PathVariable String permissionId,
                                         @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        return permissionService.deactivate(permissionId);
    }

    @PostMapping("/{permissionId}/activate")
    public PermissionResponse activate(@PathVariable String permissionId,
                                       @RequestHeader(AuditContextFilter.AUDIT_REASON_HEADER) String auditReason) {
        return permissionService.activate(permissionId);
    }

    @GetMapping("/{permissionId}/groups")
    public List<RolePermissionResponse> groupsUsingPermission(@PathVariable String permissionId) {
        return rolePermissionService.rolesForPermission(permissionId);
    }
}
