package com.smarthome.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(Map.of("error", "No tienes permiso para esta acción"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadArg(IllegalArgumentException e) {
        String m = e.getMessage() != null ? e.getMessage() : "argumento inválido";
        return ResponseEntity.badRequest().body(Map.of("error", m));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        String m = e.getMessage() != null ? e.getMessage() : "conflict";
        return ResponseEntity.status(409).body(Map.of("error", m));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(RuntimeException e) {
        int status = e.getMessage().contains("not found") ? 404
                   : e.getMessage().contains("Forbidden") ? 403
                   : e.getMessage().contains("already")   ? 409 : 400;
        return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
    }
}
