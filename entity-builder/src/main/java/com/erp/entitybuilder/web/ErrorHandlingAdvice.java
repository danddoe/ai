package com.erp.entitybuilder.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ErrorHandlingAdvice {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandlingAdvice.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException e, HttpServletRequest request) {
        return ResponseEntity.status(e.getStatus()).body(errorBody(e.getCode(), e.getMessage(), e.getDetails(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fieldErrors", e.getBindingResult().getFieldErrors().stream().map(fe -> Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage()
        )).toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody("validation_error", "Request validation failed", details, request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        Map<String, Object> details = Map.of(
                "violations", e.getConstraintViolations().stream().map(v -> Map.of(
                        "path", String.valueOf(v.getPropertyPath()),
                        "message", v.getMessage()
                )).toList()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody("validation_error", "Request validation failed", details, request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody("bad_request", "Malformed JSON request body", Map.of(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnhandled(Exception e, HttpServletRequest request) {
        log.warn("Unhandled {} on {}: {}", e.getClass().getSimpleName(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("internal_error", "Unexpected error", Map.of(), request));
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

