package com.designpattern.cognitorbac.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestLoggingFilterTests {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preservesValidCorrelationIdAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/roles");
        request.addHeader(RequestLogContext.CORRELATION_ID_HEADER, "client-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (currentRequest, currentResponse) ->
                ((MockHttpServletResponse) currentResponse).setStatus(200);

        new RequestLoggingFilter().doFilter(request, response, chain);

        assertEquals("client-request-123", response.getHeader(RequestLogContext.REQUEST_ID_HEADER));
        assertEquals("client-request-123", request.getAttribute(RequestLogContext.REQUEST_ID_ATTRIBUTE));
        assertNull(MDC.get("requestId"));
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void replacesUnsafeCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/roles");
        request.addHeader(RequestLogContext.CORRELATION_ID_HEADER, "unsafe value with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestLoggingFilter().doFilter(request, response, (req, res) -> { });

        String generated = response.getHeader(RequestLogContext.REQUEST_ID_HEADER);
        org.junit.jupiter.api.Assertions.assertNotEquals("unsafe value with spaces", generated);
        java.util.UUID.fromString(generated);
    }
}
