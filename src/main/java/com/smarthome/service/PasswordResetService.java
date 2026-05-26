package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;

    public Dto.PasswordResetResponse requestReset(Dto.PasswordResetRequest req) {
        userRepository.findByEmailIgnoreCase(req.getEmail().trim());
        return Dto.PasswordResetResponse.builder()
                .message("Si el email existe, recibirás instrucciones para restablecer tu contraseña.")
                .build();
    }
}
