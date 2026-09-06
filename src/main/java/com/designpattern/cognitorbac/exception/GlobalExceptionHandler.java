package com.designpattern.cognitorbac.exception;

import com.designpattern.cognitorbac.dto.ApiError;
import com.designpattern.cognitorbac.dto.ApiErrorCode;
import com.designpattern.cognitorbac.observability.RequestLogContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.util.List;

/** Centralized, safe exception-to-HTTP translation for the REST boundary. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoRoute(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "The requested resource was not found", request);
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ResourceConflictException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ApiErrorCode.RESOURCE_CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ApiErrorCode.RESOURCE_CONFLICT,
                "A record with these unique values already exists", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                          HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ApiErrorCode.CONCURRENT_MODIFICATION,
                "The authorization data changed concurrently; retry the request", request);
    }

    @ExceptionHandler({DataAccessException.class, TransactionSystemException.class})
    public ResponseEntity<ApiError> handleDataStore(Exception ex, HttpServletRequest request) {
        return serverError(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.DATA_STORE_UNAVAILABLE,
                "Authorization data store is temporarily unavailable", "mongodb", ex, request);
    }

    @ExceptionHandler(CognitoIntegrationException.class)
    public ResponseEntity<ApiError> handleCognito(CognitoIntegrationException ex, HttpServletRequest request) {
        String requestId = RequestLogContext.getOrCreateRequestId(request);
        var event = log.atError()
                .addKeyValue("event", "application_error")
                .addKeyValue("errorCode", ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR.name())
                .addKeyValue("component", "cognito")
                .addKeyValue("operation", ex.getOperation())
                .addKeyValue("exceptionType", ex.getClass().getSimpleName())
                .addKeyValue("httpStatus", HttpStatus.BAD_GATEWAY.value())
                .setCause(ex);
        if (ex.getCause() instanceof AwsServiceException awsException) {
            event.addKeyValue("awsStatusCode", awsException.statusCode())
                    .addKeyValue("awsRequestId", awsException.requestId());
            if (awsException.awsErrorDetails() != null) {
                event.addKeyValue("awsErrorCode", awsException.awsErrorDetails().errorCode());
            }
        }
        event.log("Request processing failed");
        return respond(HttpStatus.BAD_GATEWAY, ApiErrorCode.UPSTREAM_IDENTITY_PROVIDER_ERROR,
                "Upstream identity provider error", request, null, requestId);
    }

    @ExceptionHandler(AuditException.class)
    public ResponseEntity<ApiError> handleAudit(AuditException ex, HttpServletRequest request) {
        return serverError(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.AUDIT_UNAVAILABLE,
                "Audit service is temporarily unavailable", "audit", ex, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTHENTICATION_REQUIRED,
                "Authentication is required", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED, "Access is denied", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation).toList();
        return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                               HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldViolation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", request, violations);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex,
                                                            HttpServletRequest request) {
        if (ex.isForReturnValue()) {
            return serverError(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR,
                    "An unexpected error occurred", "application", ex, request);
        }
        return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_REQUEST,
                "Request body is missing or malformed", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                        HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
                "Invalid value for '" + ex.getName() + "'", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex,
                                                         HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.MISSING_REQUIRED_VALUE,
                "Required header '" + ex.getHeaderName() + "' is missing", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.MISSING_REQUIRED_VALUE,
                "Required parameter '" + ex.getParameterName() + "' is missing", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                            HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ApiErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method is not supported for this resource", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                               HttpServletRequest request) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Content type is not supported", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                         HttpServletRequest request) {
        return respond(HttpStatus.NOT_ACCEPTABLE, ApiErrorCode.NOT_ACCEPTABLE,
                "Requested response media type is not supported", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                           HttpServletRequest request) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Invalid request value" : ex.getMessage();
        return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return serverError(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred", "application", ex, request);
    }

    private ResponseEntity<ApiError> serverError(HttpStatus status, ApiErrorCode code, String publicMessage,
                                                  String component, Throwable exception,
                                                  HttpServletRequest request) {
        String requestId = RequestLogContext.getOrCreateRequestId(request);
        log.atError()
                .addKeyValue("event", "application_error")
                .addKeyValue("errorCode", code.name())
                .addKeyValue("component", component)
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .addKeyValue("httpStatus", status.value())
                .setCause(exception)
                .log("Request processing failed");
        return respond(status, code, publicMessage, request, null, requestId);
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, ApiErrorCode code, String message,
                                              HttpServletRequest request) {
        return respond(status, code, message, request, null,
                RequestLogContext.getOrCreateRequestId(request));
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, ApiErrorCode code, String message,
                                              HttpServletRequest request,
                                              List<ApiError.FieldViolation> violations) {
        return respond(status, code, message, request, violations,
                RequestLogContext.getOrCreateRequestId(request));
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, ApiErrorCode code, String message,
                                              HttpServletRequest request,
                                              List<ApiError.FieldViolation> violations,
                                              String requestId) {
        RequestLogContext.setErrorCode(request, code.name());
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), code, message,
                request.getRequestURI(), requestId, violations);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(RequestLogContext.REQUEST_ID_HEADER, requestId)
                .body(body);
    }

    private ApiError.FieldViolation toViolation(FieldError error) {
        return new ApiError.FieldViolation(error.getField(), error.getDefaultMessage());
    }
}
