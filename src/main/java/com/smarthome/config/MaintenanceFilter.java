package com.smarthome.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Cuando el mantenimiento está activo, solo usuarios con rol OWNER pueden usar la API
 * (salvo rutas públicas: login, registro, estado de mantenimiento, health, webhooks).
 */
@Component
@RequiredArgsConstructor
public class MaintenanceFilter extends OncePerRequestFilter {

    private final MaintenanceState maintenanceState;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!maintenanceState.isEnabled()) {
            chain.doFilter(req, res);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        String path = req.getServletPath();
        if (isPublicDuringMaintenance(path)) {
            chain.doFilter(req, res);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && isOwner(auth)) {
            chain.doFilter(req, res);
            return;
        }

        res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"error\":\"Sistema en mantenimiento. Solo el administrador (OWNER) puede acceder.\"}");
    }

    private static boolean isPublicDuringMaintenance(String path) {
        if ("/health".equals(path)) {
            return true;
        }
        if (path.startsWith("/webhook")) {
            return true;
        }
        if ("/auth/maintenance".equals(path)) {
            return true;
        }
        return "/auth/login".equals(path) || "/auth/register".equals(path);
    }

    private static boolean isOwner(Authentication auth) {
        for (GrantedAuthority a : auth.getAuthorities()) {
            if ("ROLE_OWNER".equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
