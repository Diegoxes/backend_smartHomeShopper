package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks PasswordResetService passwordResetService;

    @Test
    void requestReset_existingEmail_returnsGenericMessage() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.empty());
        Dto.PasswordResetRequest req = new Dto.PasswordResetRequest();
        req.setEmail("test@example.com");

        Dto.PasswordResetResponse response = passwordResetService.requestReset(req);

        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains("Si el email existe"));
        verify(userRepository).findByEmailIgnoreCase("test@example.com");
    }
}
