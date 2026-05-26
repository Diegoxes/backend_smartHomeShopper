package com.smarthome.controller;

import com.smarthome.config.MaintenanceState;
import com.smarthome.dto.Dto;
import com.smarthome.service.AuthService;
import com.smarthome.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MaintenanceState maintenanceState;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<Dto.AuthResponse> register(@Valid @RequestBody Dto.RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<Dto.AuthResponse> login(@Valid @RequestBody Dto.LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @GetMapping("/me")
    public ResponseEntity<Dto.AuthMeResponse> me(@AuthenticationPrincipal String userId) {
        if (userId == null) {
            throw new AccessDeniedException("No autenticado");
        }
        return ResponseEntity.ok(authService.me(userId));
    }

    /** Público: bandera de mantenimiento para la pantalla de login (sin JWT). */
    @GetMapping("/maintenance")
    public Map<String, Boolean> maintenanceStatus() {
        return Map.of("enabled", maintenanceState.isEnabled());
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Dto.PasswordResetResponse> passwordReset(@Valid @RequestBody Dto.PasswordResetRequest req) {
        return ResponseEntity.ok(passwordResetService.requestReset(req));
    }
}
