package com.designpattern.cognitorbac.permission;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Reusable, structured capability. Its coordinates are immutable after creation.
 */
@Document(collection = "nexus_permissions")
@CompoundIndex(name = "ux_permission_coordinates", def = "{'module': 1, 'resourceType': 1, 'access': 1}", unique = true)
@CompoundIndex(name = "ix_permissions_catalog", def = "{'status': 1, 'module': 1, 'resourceType': 1, 'access': 1, 'permissionId': 1}")
@CompoundIndex(name = "ix_permissions_catalog_order", def = "{'module': 1, 'resourceType': 1, 'access': 1, 'permissionId': 1}")
public class Permission {

    @Id
    private String id;
    /**
     * Stable external identifier used by API clients and nexus_role_permissions.
     * MongoDB _id remains an internal persistence identifier.
     */
    @Indexed(name = "ux_permission_id", unique = true, sparse = true)
    private String permissionId;
    private String module;
    private String resourceType;
    private String access;
    private String description;
    private PermissionStatus status;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    @Version
    private Long version;

    protected Permission() {
    }

    public Permission(String module, String resourceType, String access, String description,
                      PermissionStatus status, String actorSub) {
        this.permissionId = java.util.UUID.randomUUID().toString();
        this.module = module;
        this.resourceType = resourceType;
        this.access = access;
        this.description = description;
        this.status = status;
        this.createdBy = actorSub;
        this.updatedBy = actorSub;
    }

    public String getId() { return id; }
    public String getPermissionId() { return permissionId; }
    public String getModule() { return module; }
    public String getResourceType() { return resourceType; }
    public String getAccess() { return access; }
    public String getDescription() { return description; }
    public PermissionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }

    public void updateDescription(String description, String actorSub) {
        this.description = description;
        this.updatedBy = actorSub;
    }

    public void setStatus(PermissionStatus status, String actorSub) {
        this.status = status;
        this.updatedBy = actorSub;
    }
}
