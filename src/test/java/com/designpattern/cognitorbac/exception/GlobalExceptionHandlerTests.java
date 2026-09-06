package com.designpattern.cognitorbac.exception;

import com.designpattern.cognitorbac.dto.ApiError;
import com.designpattern.cognitorbac.dto.ApiErrorCode;
import com.designpattern.cognitorbac.observability.RequestLogContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsStableCodeAndRequestIdForExpectedErrors() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/roles/missing");
        request.addHeader(RequestLogContext.CORRELATION_ID_HEADER, "request-42");

        ResponseEntity<ApiError> response = handler.handleNotFound(
                new ResourceNotFoundException("Role not found"), request);

        assertEquals(404, response.getStatusCode().value());
        assertEquals(ApiErrorCode.RESOURCE_NOT_FOUND, response.getBody().code());
        assertEquals("request-42", response.getBody().requestId());
        assertEquals("request-42", response.getHeaders().getFirst(RequestLogContext.REQUEST_ID_HEADER));
        assertEquals("RESOURCE_NOT_FOUND", request.getAttribute(RequestLogContext.ERROR_CODE_ATTRIBUTE));
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/roles");

        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new RuntimeException("internal implementation detail"), request);

        assertEquals(500, response.getStatusCode().value());
        assertEquals(ApiErrorCode.INTERNAL_ERROR, response.getBody().code());
        assertEquals("An unexpected error occurred", response.getBody().message());
    }
}
