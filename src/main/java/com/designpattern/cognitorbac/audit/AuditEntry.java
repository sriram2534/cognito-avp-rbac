package com.designpattern.cognitorbac.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * Immutable audit record persisted to MongoDB for every write operation
 * in the RBAC system.
 *
 * <p>Collection: {@code audit_entries}</p>
 *
 * <p>Indexed fields:
 * <ul>
 *   <li>{@code actor_sub} — find all actions by a specific caller</li>
 *   <li>{@code group_name} — find all events for a specific group</li>
 *   <li>{@code action} — filter by action type</li>
 *   <li>{@code occurred_at} — time-range queries, TTL if needed</li>
 * </ul>
 */
@Document(collection = "audit_entries")
public class AuditEntry {

    @Id
    private String id;

    @Indexed
    @Field("action")
    private AuditAction action;

    @Indexed
    @Field("group_name")
    private String groupName;

    @Field("target_username")
    private String targetUsername;

    @Indexed
    @Field("aggregate_type")
    private String aggregateType;

    @Indexed
    @Field("aggregate_id")
    private String aggregateId;

    @Indexed
    @Field("role_key")
    private String roleKey;

    @Indexed
    @Field("permission_id")
    private String permissionId;

    @Field("actor_sub")
    @Indexed
    private String actorSub;

    @Field("actor_email")
    private String actorEmail;

    @Field("actor_groups")
    private java.util.List<String> actorGroups;

    @Field("reason")
    private String reason;

    @Indexed
    @Field("correlation_id")
    private String correlationId;

    @Indexed
    @Field("occurred_at")
    private Instant occurredAt;

    @Field("changes")
    private List<FieldChange> changes;

    protected AuditEntry() {
    }

    private AuditEntry(Builder builder) {
        this.action = builder.action;
        this.groupName = builder.groupName;
        this.targetUsername = builder.targetUsername;
        this.aggregateType = builder.aggregateType;
        this.aggregateId = builder.aggregateId;
        this.roleKey = builder.roleKey;
        this.permissionId = builder.permissionId;
        this.actorSub = builder.actorSub;
        this.actorEmail = builder.actorEmail;
        this.actorGroups = builder.actorGroups;
        this.reason = builder.reason;
        this.correlationId = builder.correlationId;
        this.occurredAt = Instant.now();
        this.changes = builder.changes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(AuditAction action) {
        return builder().action(action);
    }

    /** MongoDB's internal identifier is intentionally not part of the public audit API. */
    @JsonIgnore
    public String getId() { return id; }
    public AuditAction getAction() { return action; }
    public String getGroupName() { return groupName; }
    public String getTargetUsername() { return targetUsername; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getRoleKey() { return roleKey; }
    public String getPermissionId() { return permissionId; }
    public String getActorSub() { return actorSub; }
    public String getActorEmail() { return actorEmail; }
    public java.util.List<String> getActorGroups() { return actorGroups; }
    public String getReason() { return reason; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
    public List<FieldChange> getChanges() { return changes; }

    public static final class Builder {
        private AuditAction action;
        private String groupName;
        private String targetUsername;
        private String aggregateType;
        private String aggregateId;
        private String roleKey;
        private String permissionId;
        private String actorSub;
        private String actorEmail;
        private java.util.List<String> actorGroups;
        private String reason;
        private String correlationId;
        private List<FieldChange> changes;

        private Builder() {
        }

        public Builder action(AuditAction action) {
            this.action = action;
            return this;
        }

        public Builder groupName(String groupName) {
            this.groupName = groupName;
            return this;
        }

        public Builder targetUsername(String targetUsername) {
            this.targetUsername = targetUsername;
            return this;
        }

        public Builder aggregateType(String aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }

        public Builder aggregateId(String aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder roleKey(String roleKey) {
            this.roleKey = roleKey;
            return this;
        }

        public Builder permissionId(String permissionId) {
            this.permissionId = permissionId;
            return this;
        }

        public Builder actorSub(String actorSub) {
            this.actorSub = actorSub;
            return this;
        }

        public Builder actorEmail(String actorEmail) {
            this.actorEmail = actorEmail;
            return this;
        }

        public Builder actorGroups(java.util.List<String> actorGroups) {
            this.actorGroups = actorGroups;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder changes(List<FieldChange> changes) {
            this.changes = changes;
            return this;
        }

        public AuditEntry build() {
            return new AuditEntry(this);
        }
    }
}
