package com.example.urlshortener.exception;

import com.example.urlshortener.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> badRequest(
            BadRequestException ex, HttpServletRequest request) {
        log.warn("Bad request {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(
            NotFoundException ex, HttpServletRequest request) {
        log.warn("Not found {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(UrlExpiredException.class)
    public ResponseEntity<ErrorResponse> expired(
            UrlExpiredException ex, HttpServletRequest request) {
        log.warn("Expired URL {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return error(HttpStatus.GONE, ex.getMessage(), request);
    }

    @ExceptionHandler(UrlDisabledException.class)
    public ResponseEntity<ErrorResponse> disabled(
            UrlDisabledException ex, HttpServletRequest request) {
        log.warn("Disabled URL {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return error(HttpStatus.GONE, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failure {} {}: {}", request.getMethod(), request.getRequestURI(), message);
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> generic(
            Exception ex, HttpServletRequest request) {

        // Full exception + stack trace goes to logs/url-shortener.log.
        log.error(
                "Unhandled exception while processing {} {}",
                request.getMethod(),
                request.getRequestURI(),
                ex
        );

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                request
        );
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String message,
            HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.builder()
                .error(status.getReasonPhrase())
                .timestamp(Instant.now())
                .message(message)
                .status(status.value())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(response);
    }
}
