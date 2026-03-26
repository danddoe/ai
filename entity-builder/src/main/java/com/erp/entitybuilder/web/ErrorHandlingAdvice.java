package com.erp.entitybuilder.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ErrorHandlingAdvice {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandlingAdvice.class);

    private final MessageSource messageSource;

    public ErrorHandlingAdvice(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException e, HttpServletRequest request) {
        String msg = translate(request, "api.error." + e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(errorBody(e.getCode(), msg, e.getDetails(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fieldErrors", e.getBindingResult().getFieldErrors().stream().map(fe -> Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage()
        )).toList());
        String msg = translate(request, "api.error.validation_error", "Request validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody("validation_error", msg, details, request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        Map<String, Object> details = Map.of(
                "violations", e.getConstraintViolations().stream().map(v -> Map.of(
                        "path", String.valueOf(v.getPropertyPath()),
                        "message", v.getMessage()
                )).toList()
        );
        String msg = translate(request, "api.error.validation_error", "Request validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody("validation_error", msg, details, request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        String msg = translate(request, "api.error.bad_request", "Malformed JSON request body");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody("bad_request", msg, Map.of(), request));
    }

    /**
     * Spring Security 6 {@code @PreAuthorize} failures throw {@link AuthorizationDeniedException}, which is not an
     * {@link AccessDeniedException}; map both to HTTP 403 so API clients and E2E tests see a consistent status.
     */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleAccessDenied(Exception e, HttpServletRequest request) {
        String msg = translate(request, "api.error.forbidden", "Access denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorBody("forbidden", msg, Map.of(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnhandled(Exception e, HttpServletRequest request) {
        log.warn("Unhandled {} on {}: {}", e.getClass().getSimpleName(), request.getRequestURI(), e.getMessage(), e);
        String msg = translate(request, "api.error.internal_error", "Unexpected error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("internal_error", msg, Map.of(), request));
    }

    private String translate(HttpServletRequest request, String code, String defaultMessage) {
        Locale loc = Locale.forLanguageTag(RequestLocaleResolver.resolveLanguage(request));
        return messageSource.getMessage(code, null, defaultMessage, loc);
    }

    private Map<String, Object> errorBody(String code, String message, Map<String, Object> details, HttpServletRequest request) {
        return Map.of(
                "error", Map.of(
                        "code", code,
                        "message", message,
                        "details", details != null ? details : Map.of(),
                        "requestId", UUID.randomUUID().toString(),
                        "path", request.getRequestURI()
                )
        );
    }
}
