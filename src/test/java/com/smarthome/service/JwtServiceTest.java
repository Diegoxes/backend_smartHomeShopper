package com.smarthome.service;

import com.smarthome.security.SessionPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secret", "test-secret-key-with-enough-length-32");
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);
    }

    @Test
    void generateAndParseSession_roundTrip() {
        String token = jwtService.generate("user-1", "a@test.com", null, "org-1", "MANAGER");
        SessionPrincipal session = jwtService.parseSession(token);
        assertEquals("user-1", session.userId());
        assertEquals("org-1", session.orgId());
        assertEquals("MANAGER", session.orgRole());
    }

    @Test
    void isValid_invalidToken_returnsFalse() {
        assertFalse(jwtService.isValid("invalid.token.here"));
    }

    @Test
    void extractUserId_returnsSubject() {
        String token = jwtService.generate("user-99", "x@test.com", "PLATFORM_OWNER", null, null);
        assertEquals("user-99", jwtService.extractUserId(token));
    }
}
