package com.jdsu.quiz.config;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(new ApiError(
                        exception.getStatusCode().value(),
                        exception.getReason(),
                        Instant.now().toString()
                ));
    }

    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public ResponseEntity<ApiError> handleDatabaseConnection(CannotGetJdbcConnectionException exception) {
        return ResponseEntity
                .status(503)
                .body(new ApiError(
                        503,
                        "Database connection failed. Check DB_URL, DB_USERNAME, and DB_PASSWORD in backend/.env.",
                        Instant.now().toString()
                ));
    }

    public record ApiError(int status, String message, String timestamp) {
    }
}
