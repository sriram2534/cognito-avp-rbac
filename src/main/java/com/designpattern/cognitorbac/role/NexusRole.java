package com.designpattern.cognitorbac.role;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Database-owned Nexus role. Cognito is not used to store role membership. */
@Document(collection = "nexus_roles")
@CompoundIndex(name = "ux_nexus_role_coordinates", def = "{'module': 1, 'name': 1}", unique = true)
@CompoundIndex(name = "ix_nexus_roles_by_module_status", def = "{'module': 1, 'status': 1}")
@CompoundIndex(name = "ix_nexus_roles_catalog", def = "{'status': 1, 'module': 1, 'name': 1, 'roleId': 1}")
@CompoundIndex(name = "ix_nexus_roles_catalog_order", def = "{'module': 1, 'name': 1, 'roleId': 1}")
public class NexusRole {

    @Id
    private String id;
    @Indexed(name = "ux_nexus_role_id", unique = true, sparse = true)
    private String roleId;
    @Indexed(name = "ux_nexus_role_key", unique = true, sparse = true)
    private String roleKey;
    private String module;
    private String name;
    private String displayName;
    private String description;
    private NexusRoleStatus status;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    @Version
    private Long version;

    protected NexusRole() {
    }

    public NexusRole(String roleKey, String module, String name, String displayName,
                     String description, String actorSub) {
        this.roleId = UUID.randomUUID().toString();
        this.roleKey = roleKey;
        this.module = module;
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.status = NexusRoleStatus.ACTIVE;
        this.createdBy = actorSub;
        this.updatedBy = actorSub;
    }

    public String getId() { return id; }
    public String getRoleId() { return roleId; }
    public String getRoleKey() { return roleKey; }
    public String getModule() { return module; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public NexusRoleStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    public void update(String displayName, String description, String actorSub) {
        this.displayName = displayName;
        this.description = description;
        this.updatedBy = actorSub;
    }

    public void setStatus(NexusRoleStatus status, String actorSub) {
        this.status = status;
        this.updatedBy = actorSub;
    }
}
