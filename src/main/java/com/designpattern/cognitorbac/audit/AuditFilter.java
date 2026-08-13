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

    private final String groupName;
    private final String targetUsername;
    private final String actorSub;
    private final String actorEmail;
    private final List<AuditAction> actions;
    private final String changedField;
    private final Instant from;
    private final Instant to;

    private AuditFilter(Builder b) {
        this.groupName      = b.groupName;
        this.targetUsername = b.targetUsername;
        this.actorSub       = b.actorSub;
        this.actorEmail     = b.actorEmail;
        this.actions        = b.actions;
        this.changedField   = b.changedField;
        this.from           = b.from;
        this.to             = b.to;
    }

    public static Builder builder() { return new Builder(); }

    public String getGroupName()      { return groupName; }
    public String getTargetUsername() { return targetUsername; }
    public String getActorSub()       { return actorSub; }
    public String getActorEmail()     { return actorEmail; }
    public List<AuditAction> getActions() { return actions; }
    public String getChangedField()   { return changedField; }
    public Instant getFrom()          { return from; }
    public Instant getTo()            { return to; }

    public static final class Builder {
        private String groupName;
        private String targetUsername;
        private String actorSub;
        private String actorEmail;
        private List<AuditAction> actions;
        private String changedField;
        private Instant from;
        private Instant to;

        public Builder groupName(String v)      { this.groupName = v; return this; }
        public Builder targetUsername(String v) { this.targetUsername = v; return this; }
        public Builder actorSub(String v)       { this.actorSub = v; return this; }
        public Builder actorEmail(String v)     { this.actorEmail = v; return this; }
        public Builder actions(List<AuditAction> v) { this.actions = v; return this; }
        public Builder changedField(String v)   { this.changedField = v; return this; }
        public Builder from(Instant v)          { this.from = v; return this; }
        public Builder to(Instant v)            { this.to = v; return this; }

        public AuditFilter build() { return new AuditFilter(this); }
    }
}
