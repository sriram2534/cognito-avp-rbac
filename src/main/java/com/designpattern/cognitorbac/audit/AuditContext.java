package com.designpattern.cognitorbac.audit;

import java.util.List;

/**
 * Per-request audit context stored in a ThreadLocal.
 * Populated by {@link AuditContextFilter} at the start of every request
 * and cleared at the end to prevent memory leaks.
 *
 * <p>The {@code reason} field carries the mandatory {@code X-Audit-Reason}
 * header value so the AOP aspect can attach it to every audit entry without
 * needing it threaded through every service method signature.</p>
 */
public final class AuditContext {

    private static final ThreadLocal<AuditContext> HOLDER = new ThreadLocal<>();

    private final String actorSub;
    private final String actorEmail;
    private final List<String> actorGroups;
    private final String reason;
    private final String correlationId;

    private AuditContext(String actorSub, String actorEmail, List<String> actorGroups, String reason,
                         String correlationId) {
        this.actorSub = actorSub;
        this.actorEmail = actorEmail;
        this.actorGroups = actorGroups;
        this.reason = reason;
        this.correlationId = correlationId;
    }

    public static void set(String actorSub, String actorEmail, List<String> actorGroups, String reason,
                           String correlationId) {
        HOLDER.set(new AuditContext(actorSub, actorEmail, actorGroups, reason, correlationId));
    }

    public static AuditContext current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public String getActorSub() { return actorSub; }
    public String getActorEmail() { return actorEmail; }
    public List<String> getActorGroups() { return actorGroups; }
    public String getReason() { return reason; }
    public String getCorrelationId() { return correlationId; }
}
