package com.smarthome.service;

import com.smarthome.security.SessionPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final int MIN_KEY_BYTES = 32;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    SecretKey key() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret no puede estar vacío; define JWT_SECRET en el entorno.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_KEY_BYTES) {
            bytes = sha256(bytes);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public String generate(String userId, String email, String platformRole, String orgId, String orgRole) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        if (platformRole != null) {
            claims.put("platformRole", platformRole);
        }
        if (orgId != null) {
            claims.put("orgId", orgId);
        }
        if (orgRole != null) {
            claims.put("orgRole", orgRole);
        }
        return Jwts.builder()
                .subject(userId)
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key())
                .compact();
    }

    public SessionPrincipal parseSession(String token) {
        Claims claims = parseClaims(token);
        return new SessionPrincipal(
                claims.getSubject(),
                claims.get("orgId", String.class),
                claims.get("orgRole", String.class),
                claims.get("platformRole", String.class)
        );
    }

    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload();
    }
}
