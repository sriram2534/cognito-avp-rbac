package com.designpattern.cognitorbac.audit;

import java.time.Instant;
import java.util.List;

/**
 * Immutable filter bag for composable audit queries.
 * Every field is optional — only non-null values are applied as criteria.
 *
 * <p>Build via {@link #builder()} and pass to
 * {@link AuditQueryService#search(AuditFilter, org.springframework.data.domain.Pageable)}.</p>
 */
public class AuditFilter {

    private final String roleName;
    private final String userSub;
    private final String userEmail;
    private final String roleKey;
    private final String permissionId;
    private final String aggregateType;
    private final String aggregateId;
    private final List<AuditAction> actions;
    private final String changedField;
    private final Instant from;
    private final Instant to;

    private AuditFilter(Builder b) {
        this.roleName       = b.roleName;
        this.userSub        = b.userSub;
        this.userEmail      = b.userEmail;
        this.roleKey        = b.roleKey;
        this.permissionId   = b.permissionId;
        this.aggregateType  = b.aggregateType;
        this.aggregateId    = b.aggregateId;
        this.actions        = b.actions;
        this.changedField   = b.changedField;
        this.from           = b.from;
        this.to             = b.to;
    }

    public static Builder builder() { return new Builder(); }

    public String getRoleName()       { return roleName; }
    public String getUserSub()        { return userSub; }
    public String getUserEmail()      { return userEmail; }
    public String getRoleKey()        { return roleKey; }
    public String getPermissionId()   { return permissionId; }
    public String getAggregateType()  { return aggregateType; }
    public String getAggregateId()    { return aggregateId; }
    public List<AuditAction> getActions() { return actions; }
    public String getChangedField()   { return changedField; }
    public Instant getFrom()          { return from; }
    public Instant getTo()            { return to; }

    public static final class Builder {
        private String roleName;
        private String userSub;
        private String userEmail;
        private String roleKey;
        private String permissionId;
        private String aggregateType;
        private String aggregateId;
        private List<AuditAction> actions;
        private String changedField;
        private Instant from;
        private Instant to;

        public Builder roleName(String v)       { this.roleName = v; return this; }
        public Builder userSub(String v)        { this.userSub = v; return this; }
        public Builder userEmail(String v)      { this.userEmail = v; return this; }
        public Builder roleKey(String v)        { this.roleKey = v; return this; }
        public Builder permissionId(String v)   { this.permissionId = v; return this; }
        public Builder aggregateType(String v)  { this.aggregateType = v; return this; }
        public Builder aggregateId(String v)    { this.aggregateId = v; return this; }
        public Builder actions(List<AuditAction> v) { this.actions = v; return this; }
        public Builder changedField(String v)   { this.changedField = v; return this; }
        public Builder from(Instant v)          { this.from = v; return this; }
        public Builder to(Instant v)            { this.to = v; return this; }

        public AuditFilter build() { return new AuditFilter(this); }
    }
}
