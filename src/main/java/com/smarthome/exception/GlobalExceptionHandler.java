package com.smarthome.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(RuntimeException e) {
        int status = e.getMessage().contains("not found") ? 404
                   : e.getMessage().contains("Forbidden") ? 403
                   : e.getMessage().contains("already")   ? 409 : 400;
        return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
    }
}
