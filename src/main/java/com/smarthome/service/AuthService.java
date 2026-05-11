package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Role;
import com.smarthome.entity.User;
import com.smarthome.repository.RoleRepository;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Misma forma de guardar y buscar el email (evita fallos login por mayúsculas / espacios). */
    private static String normalizeEmail(String email) { 
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public Dto.AuthResponse register(Dto.RegisterRequest req) {
        String email = normalizeEmail(req.getEmail());
        if (userRepo.existsByEmailIgnoreCase(email))
            throw new RuntimeException("Email already registered");

        Role defaultRole = roleRepo.findByName("MEMBER")
                .orElseThrow(() -> new IllegalStateException("Rol MEMBER no configurado; ejecuta el arranque de la app"));

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName().trim())
                .whatsappNumber(req.getWhatsappNumber() != null ? req.getWhatsappNumber().trim() : null)
                .role(defaultRole)
                .build();
        userRepo.save(user);

        String token = jwtService.generate(user.getId(), user.getEmail(), user.getRole().getName());
        return Dto.AuthResponse.builder()
                .token(token).userId(user.getId())
                .name(user.getName()).email(user.getEmail())
                .role(user.getRole().getName())
                .build();
    }

    @Transactional(readOnly = true)
    public Dto.AuthResponse login(Dto.LoginRequest req) {
        String email = normalizeEmail(req.getEmail());
        User user = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword()))
            throw new RuntimeException("Invalid credentials");
        if (user.getRole() == null)
            throw new RuntimeException("Cuenta sin rol asignado; espera a que el sistema termine de actualizar o contacta al administrador.");

        String token = jwtService.generate(user.getId(), user.getEmail(), user.getRole().getName());
        return Dto.AuthResponse.builder()
                .token(token).userId(user.getId())
                .name(user.getName()).email(user.getEmail())
                .role(user.getRole().getName())
                .build();
    }
}
