package com.designpattern.cognitorbac.audit;

import com.designpattern.cognitorbac.security.CallerContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Servlet filter that runs once per request to populate {@link AuditContext}
 * with the authenticated caller's identity and the mandatory audit reason header.
 *
 * <p>The {@code X-Audit-Reason} header is required on all write operations.
 * Validation that it is present is enforced at the controller layer via
 * {@code @RequestHeader}; this filter just makes it available to the AOP aspect
 * without coupling the service layer to servlet concerns.</p>
 */
@Component
public class AuditContextFilter extends OncePerRequestFilter {

    public static final String AUDIT_REASON_HEADER = "X-Audit-Reason";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final Set<String> AUDITED_API_PREFIXES = Set.of(
            "/api/v1/groups",
            "/api/v1/permissions",
            "/api/v1/policies",
            "/api/v1/users"
    );
    private static final Set<String> AUDITED_HTTP_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final CallerContext callerContext;

    public AuditContextFilter(CallerContext callerContext) {
        this.callerContext = callerContext;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!AUDITED_HTTP_METHODS.contains(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return AUDITED_API_PREFIXES.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = correlationId(request);
        try {
            String reason     = request.getHeader(AUDIT_REASON_HEADER);
            String actorSub   = callerContext.getCallerSub();
            String actorEmail = resolveEmail();
            List<String> actorGroups = callerContext.getCallerGroups();

            MDC.put("requestId", requestId);
            MDC.put("correlationId", requestId);
            MDC.put("actorSub",   actorSub   != null ? actorSub   : "anonymous");
            MDC.put("actorEmail", actorEmail != null ? actorEmail : "unknown");
            MDC.put("httpMethod", request.getMethod());
            MDC.put("httpPath",   request.getRequestURI());

            response.setHeader("X-Request-Id", requestId);

            // The request ID is the correlation ID for this service. It is also
            // returned to callers, so audit/outbox records can be correlated.
            AuditContext.set(actorSub, actorEmail, actorGroups, reason, requestId);
            filterChain.doFilter(request, response);
        } finally {
            AuditContext.clear();
            MDC.remove("requestId");
            MDC.remove("correlationId");
            MDC.remove("actorSub");
            MDC.remove("actorEmail");
            MDC.remove("httpMethod");
            MDC.remove("httpPath");
        }
    }

    private String correlationId(HttpServletRequest request) {
        String inbound = request.getHeader(CORRELATION_ID_HEADER);
        if (inbound != null && inbound.matches("[A-Za-z0-9._-]{1,128}")) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }

    private String resolveEmail() {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder
                            .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                return jwt.getClaimAsString("email");
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
