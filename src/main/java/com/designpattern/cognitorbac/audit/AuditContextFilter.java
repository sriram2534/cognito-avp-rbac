package com.designpattern.cognitorbac.audit;

import com.designpattern.cognitorbac.avp.SecurityContextHelper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
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

    private final SecurityContextHelper securityContextHelper;

    public AuditContextFilter(SecurityContextHelper securityContextHelper) {
        this.securityContextHelper = securityContextHelper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        try {
            String reason     = request.getHeader(AUDIT_REASON_HEADER);
            String actorSub   = securityContextHelper.getCallerSub();
            String actorEmail = resolveEmail();
            List<String> actorGroups = securityContextHelper.getCallerGroups();

            MDC.put("requestId",  requestId);
            MDC.put("actorSub",   actorSub   != null ? actorSub   : "anonymous");
            MDC.put("actorEmail", actorEmail != null ? actorEmail : "unknown");
            MDC.put("httpMethod", request.getMethod());
            MDC.put("httpPath",   request.getRequestURI());

            response.setHeader("X-Request-Id", requestId);

            AuditContext.set(actorSub, actorEmail, actorGroups, reason);
            filterChain.doFilter(request, response);
        } finally {
            AuditContext.clear();
            MDC.clear();
        }
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
