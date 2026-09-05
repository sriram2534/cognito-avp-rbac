package com.designpattern.cognitorbac.audit;

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

    private final String userSub;
    private final String userEmail;
    private final String reason;
    private final String correlationId;

    private AuditContext(String userSub, String userEmail, String reason,
                         String correlationId) {
        this.userSub = userSub;
        this.userEmail = userEmail;
        this.reason = reason;
        this.correlationId = correlationId;
    }

    public static void set(String userSub, String userEmail, String reason, String correlationId) {
        HOLDER.set(new AuditContext(userSub, userEmail, reason, correlationId));
    }

    public static AuditContext current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public String getUserSub() { return userSub; }
    public String getUserEmail() { return userEmail; }
    public String getReason() { return reason; }
    public String getCorrelationId() { return correlationId; }
}
