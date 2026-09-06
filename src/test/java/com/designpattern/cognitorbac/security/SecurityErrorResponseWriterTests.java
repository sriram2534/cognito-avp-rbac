package com.designpattern.cognitorbac.security;

import com.designpattern.cognitorbac.dto.ApiErrorCode;
import com.designpattern.cognitorbac.observability.RequestLogContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityErrorResponseWriterTests {

    @Test
    void writesTheStandardApiErrorContract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/roles");
        request.addHeader(RequestLogContext.CORRELATION_ID_HEADER, "security-request-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityErrorResponseWriter(new ObjectMapper()).write(request, response, HttpStatus.UNAUTHORIZED,
                ApiErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required");

        assertEquals(401, response.getStatus());
        assertEquals("security-request-1", response.getHeader(RequestLogContext.REQUEST_ID_HEADER));
        assertTrue(response.getContentAsString().contains("\"code\":\"AUTHENTICATION_REQUIRED\""));
        assertTrue(response.getContentAsString().contains("\"requestId\":\"security-request-1\""));
        assertFalse(response.getContentAsString().contains("\"violations\""));
    }
}
