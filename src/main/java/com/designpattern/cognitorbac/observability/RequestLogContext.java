package com.designpattern.cognitorbac.observability;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/** Shared request attributes and safe correlation-ID handling. */
public final class RequestLogContext {
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = RequestLogContext.class.getName() + ".requestId";
    public static final String ERROR_CODE_ATTRIBUTE = RequestLogContext.class.getName() + ".errorCode";
    public static final String USER_SUB_ATTRIBUTE = RequestLogContext.class.getName() + ".userSub";
    private static final String CORRELATION_ID_PATTERN = "[A-Za-z0-9._-]{1,128}";

    private RequestLogContext() {
    }

    public static String getOrCreateRequestId(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        String inbound = request.getHeader(CORRELATION_ID_HEADER);
        String requestId = inbound != null && inbound.matches(CORRELATION_ID_PATTERN)
                ? inbound : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        return requestId;
    }

    public static void setErrorCode(HttpServletRequest request, String errorCode) {
        request.setAttribute(ERROR_CODE_ATTRIBUTE, errorCode);
    }

    public static String stringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
