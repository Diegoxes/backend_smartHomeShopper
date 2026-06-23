package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.*;
import com.smarthome.repository.*;
import com.smarthome.security.SessionPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final OrganizationMemberRepository memberRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserPermissionService userPermissionService;

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
                .role(null)
                .build();
        userRepo.save(user);

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public Dto.AuthResponse login(Dto.LoginRequest req) {
        String email = normalizeEmail(req.getEmail());
        User user = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword()))
            throw new RuntimeException("Invalid credentials");
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public Dto.AuthMeResponse me(String userId) {
        User user = userRepo.findByIdWithRbac(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Optional<OrganizationMember> membership = memberRepo.findByUserId(userId);
        String platformRole = user.getRole() != null ? user.getRole().getName() : null;
        boolean isPlatformOwner = "PLATFORM_OWNER".equals(platformRole);
        String orgRole = isPlatformOwner ? null : membership.map(m -> m.getOrgRole().name()).orElse(null);
        String orgId = isPlatformOwner ? null : membership.map(m -> m.getOrganization().getId()).orElse(null);
        String orgStatus = isPlatformOwner ? null : membership.map(m -> m.getOrganization().getStatus().name()).orElse(null);
        String displayRole = platformRole != null ? platformRole : (orgRole != null ? orgRole : "PENDING");

        SessionPrincipal session = new SessionPrincipal(userId, orgId, orgRole, platformRole);
        boolean grantPermissions = isPlatformOwner || "ACTIVE".equals(orgStatus);

        return Dto.AuthMeResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(displayRole)
                .platformRole(platformRole)
                .orgRole(orgRole)
                .orgId(orgId)
                .orgStatus(orgStatus)
                .needsOnboarding(orgId == null && !"PLATFORM_OWNER".equals(platformRole))
                .permissions(grantPermissions ? userPermissionService.modulePermissionsForSession(session) : List.of())
                .build();
    }

    private Dto.AuthResponse buildAuthResponse(User user) {
        User full = userRepo.findByIdWithRbac(user.getId())
                .orElseThrow(() -> new IllegalStateException("Usuario recién creado no encontrado"));
        Optional<OrganizationMember> membership = memberRepo.findByUserId(full.getId());

        String platformRole = full.getRole() != null ? full.getRole().getName() : null;
        boolean isPlatformOwner = "PLATFORM_OWNER".equals(platformRole);
        String orgId = isPlatformOwner ? null : membership.map(m -> m.getOrganization().getId()).orElse(null);
        String orgRole = isPlatformOwner ? null : membership.map(m -> m.getOrgRole().name()).orElse(null);
        String orgStatus = isPlatformOwner ? null : membership.map(m -> m.getOrganization().getStatus().name()).orElse(null);
        String displayRole = platformRole != null ? platformRole : (orgRole != null ? orgRole : "PENDING");

        String token = jwtService.generate(full.getId(), full.getEmail(), platformRole, orgId, orgRole);
        SessionPrincipal session = new SessionPrincipal(full.getId(), orgId, orgRole, platformRole);
        boolean grantPermissions = isPlatformOwner || "ACTIVE".equals(orgStatus);

        return Dto.AuthResponse.builder()
                .token(token)
                .userId(full.getId())
                .name(full.getName())
                .email(full.getEmail())
                .role(displayRole)
                .platformRole(platformRole)
                .orgRole(orgRole)
                .orgId(orgId)
                .orgStatus(orgStatus)
                .needsOnboarding(orgId == null && !"PLATFORM_OWNER".equals(platformRole))
                .permissions(grantPermissions ? userPermissionService.modulePermissionsForSession(session) : List.of())
                .build();
    }
}
