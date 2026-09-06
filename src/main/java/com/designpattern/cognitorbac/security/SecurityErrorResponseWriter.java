package com.designpattern.cognitorbac.security;

import com.designpattern.cognitorbac.dto.ApiError;
import com.designpattern.cognitorbac.dto.ApiErrorCode;
import com.designpattern.cognitorbac.observability.RequestLogContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** Writes the same safe JSON error contract for failures inside the security filter chain. */
@Component
public class SecurityErrorResponseWriter {
    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      ApiErrorCode code, String message) throws IOException {
        String requestId = RequestLogContext.getOrCreateRequestId(request);
        RequestLogContext.setErrorCode(request, code.name());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(RequestLogContext.REQUEST_ID_HEADER, requestId);
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                status.value(), status.getReasonPhrase(), code, message, request.getRequestURI(), requestId));
    }
}
