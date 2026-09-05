package com.designpattern.cognitorbac.role;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Durable user-to-role relationship keyed by the Cognito subject claim. */
@Document(collection = "nexus_user_roles")
@CompoundIndex(name = "ux_nexus_user_role", def = "{'userSub': 1, 'roleId': 1}", unique = true)
@CompoundIndex(name = "ix_nexus_user_roles_by_user", def = "{'userSub': 1, 'status': 1, 'roleId': 1}")
@CompoundIndex(name = "ix_nexus_user_roles_by_role", def = "{'roleId': 1, 'status': 1, 'userSub': 1}")
public class NexusUserRole {

    @Id
    private String id;
    private String userSub;
    private String roleId;
    private NexusUserRoleStatus status;
    private Instant assignedAt;
    private Instant removedAt;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    private String assignedBy;
    private String updatedBy;
    @Version
    private Long version;

    protected NexusUserRole() {
    }

    public NexusUserRole(String userSub, String roleId, String actorSub) {
        this.userSub = userSub;
        this.roleId = roleId;
        this.status = NexusUserRoleStatus.ACTIVE;
        this.assignedAt = Instant.now();
        this.assignedBy = actorSub;
        this.updatedBy = actorSub;
    }

    public String getId() { return id; }
    public String getUserSub() { return userSub; }
    public String getRoleId() { return roleId; }
    public NexusUserRoleStatus getStatus() { return status; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getRemovedAt() { return removedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    public void restore(String actorSub) {
        this.status = NexusUserRoleStatus.ACTIVE;
        this.assignedAt = Instant.now();
        this.removedAt = null;
        this.updatedBy = actorSub;
    }

    public void remove(String actorSub) {
        this.status = NexusUserRoleStatus.REMOVED;
        this.removedAt = Instant.now();
        this.updatedBy = actorSub;
    }
}
