package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.User;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public Dto.AuthResponse register(Dto.RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail()))
            throw new RuntimeException("Email already registered");

        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName())
                .whatsappNumber(req.getWhatsappNumber())
                .build();
        userRepo.save(user);

        String token = jwtService.generate(user.getId(), user.getEmail());
        return Dto.AuthResponse.builder()
                .token(token).userId(user.getId())
                .name(user.getName()).email(user.getEmail())
                .build();
    }

    public Dto.AuthResponse login(Dto.LoginRequest req) {
        User user = userRepo.findByEmail(req.getEmail())
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
