package com.designpattern.cognitorbac.security;

import com.designpattern.cognitorbac.dto.ApiErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Returns a JSON 403 for authorization failures raised inside Spring Security. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private final SecurityErrorResponseWriter writer;

    public RestAccessDeniedHandler(SecurityErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException, ServletException {
        writer.write(request, response, HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED, "Access is denied");
    }
}
