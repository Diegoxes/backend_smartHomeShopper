package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.User;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
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

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName().trim())
                .whatsappNumber(req.getWhatsappNumber() != null ? req.getWhatsappNumber().trim() : null)
                .build();
        userRepo.save(user);

        String token = jwtService.generate(user.getId(), user.getEmail());
        return Dto.AuthResponse.builder()
                .token(token).userId(user.getId())
                .name(user.getName()).email(user.getEmail())
                .build();
    }

    public Dto.AuthResponse login(Dto.LoginRequest req) {
        String email = normalizeEmail(req.getEmail());
        User user = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword()))
            throw new RuntimeException("Invalid credentials");

        String token = jwtService.generate(user.getId(), user.getEmail());
        return Dto.AuthResponse.builder()
                .token(token).userId(user.getId())
                .name(user.getName()).email(user.getEmail())
                .build();
    }
}
