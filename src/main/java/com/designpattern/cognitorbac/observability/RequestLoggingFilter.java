package com.designpattern.cognitorbac.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Emits one CloudWatch-queryable completion event for every HTTP request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String AWS_TRACE_HEADER = "X-Amzn-Trace-Id";
    private static final Pattern AWS_ROOT_TRACE_ID = Pattern.compile(
            "(?:^|;)Root=(1-[0-9a-fA-F]{8}-[0-9a-fA-F]{24})(?:;|$)");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = RequestLogContext.getOrCreateRequestId(request);
        String traceId = safeTraceId(request.getHeader(AWS_TRACE_HEADER));
        Throwable failure = null;

        response.setHeader(RequestLogContext.REQUEST_ID_HEADER, requestId);
        MDC.put("requestId", requestId);
        MDC.put("correlationId", requestId);
        MDC.put("httpMethod", request.getMethod());
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error ex) {
            failure = ex;
            throw ex;
        } finally {
            int status = failure != null && response.getStatus() < 400
                    ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            logCompletion(request, status, durationMs, failure);
            MDC.remove("requestId");
            MDC.remove("correlationId");
            MDC.remove("httpMethod");
            MDC.remove("traceId");
        }
    }

    private void logCompletion(HttpServletRequest request, int status, long durationMs, Throwable failure) {
        LoggingEventBuilder event = loggerFor(request.getRequestURI(), status);
        event.addKeyValue("event", "http_request_completed")
                .addKeyValue("httpRoute", route(request))
                .addKeyValue("httpStatus", status)
                .addKeyValue("outcome", outcome(status))
                .addKeyValue("durationMs", durationMs);
        addIfPresent(event, "errorCode",
                RequestLogContext.stringAttribute(request, RequestLogContext.ERROR_CODE_ATTRIBUTE));
        addIfPresent(event, "userSub",
                RequestLogContext.stringAttribute(request, RequestLogContext.USER_SUB_ATTRIBUTE));
        if (failure != null) {
            event.addKeyValue("exceptionType", failure.getClass().getSimpleName());
        }
        event.log("HTTP request completed");
    }

    private LoggingEventBuilder loggerFor(String path, int status) {
        if (status >= 500) {
            return log.atError();
        }
        if (status >= 400) {
            return log.atWarn();
        }
        if (path.startsWith("/actuator/health")) {
            return log.atDebug();
        }
        return log.atInfo();
    }

    private static String outcome(int status) {
        if (status >= 500) return "SERVER_ERROR";
        if (status >= 400) return "CLIENT_ERROR";
        return "SUCCESS";
    }

    private static void addIfPresent(LoggingEventBuilder event, String key, String value) {
        if (value != null) {
            event.addKeyValue(key, value);
        }
    }

    private static String safeTraceId(String value) {
        if (value == null || value.isBlank() || value.length() > 256) {
            return null;
        }
        Matcher matcher = AWS_ROOT_TRACE_ID.matcher(value.replace(" ", ""));
        return matcher.find() ? matcher.group(1).toLowerCase(java.util.Locale.ROOT) : null;
    }

    private static String route(HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (route != null) {
            return route.toString();
        }
        String path = request.getRequestURI();
        String normalized = path
                .replaceFirst("^(/api/v1/users)/[^/]+$", "$1/{username}")
                .replaceFirst("^(/api/v1/roles/[^/]+/users)/[^/]+$", "$1/{userSub}")
                .replaceFirst("^(/api/v1/audit/users)/[^/]+$", "$1/{userSub}")
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,36}", "{id}");
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }
}
