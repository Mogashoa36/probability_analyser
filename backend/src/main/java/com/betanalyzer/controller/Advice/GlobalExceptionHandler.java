
package com.betanalyzer.controller.Advice;

import com.betanalyzer.service.external.ExternalDataException;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            NoSuchElementException e,
            HttpServletRequest request) {

        return build(
                HttpStatus.NOT_FOUND,
                "Not found",
                e.getMessage(),
                request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException e,
            HttpServletRequest request) {

        return build(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                e.getMessage(),
                request);
    }

    @ExceptionHandler(ExternalDataException.class)
    public ResponseEntity<Map<String, Object>> handleExternalData(
            ExternalDataException e,
            HttpServletRequest request) {

        return build(
                HttpStatus.BAD_GATEWAY,
                "Upstream provider error",
                e.getMessage(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException e,
            HttpServletRequest request) {

        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");

        return build(
                HttpStatus.BAD_REQUEST,
                "Validation error",
                message,
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e,
            HttpServletRequest request) {

        String name = e.getName() != null
                ? e.getName()
                : "parameter";

        String value = e.getValue() != null
                ? String.valueOf(e.getValue())
                : "unknown";

        return build(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Invalid value '" + value + "' for " + name,
                request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandler(
            NoHandlerFoundException e,
            HttpServletRequest request) {

        return build(
                HttpStatus.NOT_FOUND,
                "Not found",
                "No handler for " + e.getRequestURL(),
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception e,
            HttpServletRequest request) {

        logger.error(
                "Unexpected error handling {} {}",
                request.getMethod(),
                request.getRequestURI(),
                e);

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please contact support if the problem persists.",
                request);
    }

    private ResponseEntity<Map<String, Object>> build(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message == null ? "" : message);
        body.put("path", request.getRequestURI());

        return ResponseEntity
                .status(status)
                .body(body);
    }
}
