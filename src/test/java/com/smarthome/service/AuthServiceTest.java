package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.*;
import com.smarthome.repository.*;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepo;
    @Mock RoleRepository roleRepo;
    @Mock OrganizationMemberRepository memberRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserPermissionService userPermissionService;
    @InjectMocks AuthService authService;

    @Test
    void register_duplicateEmail_throws() {
        when(userRepo.existsByEmailIgnoreCase("new@example.com")).thenReturn(true);
        assertThrows(RuntimeException.class, () -> authService.register(TestFixtures.registerRequest()));
    }

    @Test
    void register_success() {
        Dto.RegisterRequest req = TestFixtures.registerRequest();
        when(userRepo.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("new-user");
            return u;
        });
        User saved = User.builder().id("new-user").email("new@example.com").name("New User").build();
        when(userRepo.findByIdWithRbac("new-user")).thenReturn(Optional.of(saved));
        when(memberRepo.findByUserId("new-user")).thenReturn(Optional.empty());
        when(jwtService.generate(any(), any(), any(), any(), any())).thenReturn("token-123");

        Dto.AuthResponse response = authService.register(req);

        assertEquals("token-123", response.getToken());
        assertEquals("new-user", response.getUserId());
        assertTrue(response.isNeedsOnboarding());
    }

    @Test
    void login_invalidCredentials_throws() {
        when(userRepo.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> authService.login(TestFixtures.loginRequest()));
    }

    @Test
    void login_success() {
        User user = TestFixtures.user();
        when(userRepo.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(userRepo.findByIdWithRbac(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
        when(memberRepo.findByUserId(TestFixtures.USER_ID)).thenReturn(Optional.empty());
        when(jwtService.generate(any(), any(), any(), any(), any())).thenReturn("jwt");

        Dto.AuthResponse response = authService.login(TestFixtures.loginRequest());

        assertEquals("jwt", response.getToken());
        assertEquals(TestFixtures.USER_ID, response.getUserId());
    }
}
