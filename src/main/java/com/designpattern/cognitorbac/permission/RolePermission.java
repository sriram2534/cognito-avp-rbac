package com.designpattern.cognitorbac.permission;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Relationship between a Cognito role (group) and a reusable permission. */
@Document(collection = "role_permissions")
@CompoundIndex(name = "ux_role_permission", def = "{'roleKey': 1, 'permissionId': 1}", unique = true)
@CompoundIndex(name = "ix_role_permissions_by_role", def = "{'roleKey': 1, 'status': 1}")
@CompoundIndex(name = "ix_role_permissions_by_permission", def = "{'permissionId': 1, 'status': 1}")
public class RolePermission {

    @Id
    private String id;
    private String roleKey;
    private String permissionId;
    private RolePermissionStatus status;
    private Instant validFrom;
    private Instant validUntil;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    @Version
    private Long version;

    protected RolePermission() {
    }

    public RolePermission(String roleKey, String permissionId, String actorSub) {
        this.roleKey = roleKey;
        this.permissionId = permissionId;
        this.status = RolePermissionStatus.ACTIVE;
        this.createdBy = actorSub;
        this.updatedBy = actorSub;
    }

    public String getId() { return id; }
    public String getRoleKey() { return roleKey; }
    public String getPermissionId() { return permissionId; }
    public RolePermissionStatus getStatus() { return status; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }

    public void restore(String actorSub) {
        this.status = RolePermissionStatus.ACTIVE;
        this.updatedBy = actorSub;
    }

    public void revoke(String actorSub) {
        this.status = RolePermissionStatus.REVOKED;
        this.updatedBy = actorSub;
    }
}
