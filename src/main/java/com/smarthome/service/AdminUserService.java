package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Organization;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.entity.Role;
import com.smarthome.entity.User;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.RoleRepository;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Set<String> ORG_ROLE_NAMES = Set.of("MANAGER", "MEMBER", "VIEWER");
    private static final String PLATFORM_OWNER = "PLATFORM_OWNER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public List<Dto.AdminUserRowDto> listUsers() {
        return userRepository.findAllWithRole().stream()
                .map(this::toRowDto)
                .toList();
    }

    @Transactional
    public Dto.AdminUserRowDto createUser(Dto.AdminCreateUserRequest req) {
        String email = normalizeEmail(req.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new RuntimeException("Email already registered");
        }

        Role role = roleRepository.findById(req.getRoleId())
                .orElseThrow(() -> new RuntimeException("Rol not found"));
        String roleName = role.getName();

        if (PLATFORM_OWNER.equals(roleName)) {
            if (req.getOrganizationId() != null && !req.getOrganizationId().isBlank()) {
                throw new RuntimeException("PLATFORM_OWNER no pertenece a una organización; no indiques organizationId");
            }
        } else if (ORG_ROLE_NAMES.contains(roleName)) {
            if (req.getOrganizationId() == null || req.getOrganizationId().isBlank()) {
                throw new RuntimeException(
                        "Debes seleccionar una organización para el rol " + roleName
                                + ". MANAGER, MEMBER y VIEWER operan dentro de una empresa.");
            }
        } else {
            throw new RuntimeException("Rol no soportado para creación: " + roleName);
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName().trim())
                .whatsappNumber(req.getWhatsappNumber() != null ? req.getWhatsappNumber().trim() : null)
                .role(PLATFORM_OWNER.equals(roleName) ? role : null)
                .build();
        userRepository.save(user);

        if (ORG_ROLE_NAMES.contains(roleName)) {
            attachToOrganization(user, req.getOrganizationId(), OrganizationMember.OrgRole.valueOf(roleName));
        }

        return toRowDto(userRepository.findByIdWithRbac(user.getId())
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado tras crear")));
    }

    @Transactional
    public void updateUserRole(String userId, Dto.AdminUpdateUserRoleRequest req) {
        Role newRole = roleRepository.findById(req.getRoleId())
                .orElseThrow(() -> new RuntimeException("Rol not found"));

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String previousPlatform = target.getRole() != null ? target.getRole().getName() : null;
        if (PLATFORM_OWNER.equals(previousPlatform) && !PLATFORM_OWNER.equals(newRole.getName())) {
            if (userRepository.countByRole_Name(PLATFORM_OWNER) <= 1) {
                throw new RuntimeException("Forbidden: debe existir al menos un usuario con rol PLATFORM_OWNER");
            }
        }

        applyRoleOnUpdate(target, newRole, req.getOrganizationId());
        userRepository.save(target);
    }

    private void applyRoleOnUpdate(User user, Role newRole, String organizationId) {
        String roleName = newRole.getName();
        OrganizationMember membership = memberRepository.findByUserId(user.getId()).orElse(null);

        if (PLATFORM_OWNER.equals(roleName)) {
            if (membership != null) {
                memberRepository.delete(membership);
            }
            user.setRole(newRole);
            return;
        }

        if (ORG_ROLE_NAMES.contains(roleName)) {
            user.setRole(null);
            OrganizationMember.OrgRole orgRole = OrganizationMember.OrgRole.valueOf(roleName);

            if (membership != null) {
                assertSingleManager(membership.getOrganization().getId(), orgRole, membership.getId());
                membership.setOrgRole(orgRole);
                memberRepository.save(membership);
            } else {
                attachToOrganization(user, organizationId, orgRole);
            }
            return;
        }

        throw new RuntimeException("Rol no soportado: " + roleName);
    }

    private void attachToOrganization(User user, String organizationId, OrganizationMember.OrgRole orgRole) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organización no encontrada"));

        if (memberRepository.findByUserId(user.getId()).isPresent()) {
            throw new RuntimeException("El usuario ya pertenece a una organización");
        }

        long count = memberRepository.countByOrganizationId(org.getId());
        if (count >= org.getMaxMembers()) {
            throw new RuntimeException("Límite de miembros alcanzado en la organización (" + org.getMaxMembers() + ")");
        }

        assertSingleManager(org.getId(), orgRole, null);

        memberRepository.save(OrganizationMember.builder()
                .organization(org)
                .user(user)
                .orgRole(orgRole)
                .build());
    }

    private void assertSingleManager(String orgId, OrganizationMember.OrgRole orgRole, String excludeMemberId) {
        if (orgRole != OrganizationMember.OrgRole.MANAGER) {
            return;
        }
        long managers = memberRepository.countByOrganizationIdAndOrgRole(orgId, OrganizationMember.OrgRole.MANAGER);
        if (excludeMemberId != null && managers > 0) {
            OrganizationMember existing = memberRepository.findById(excludeMemberId).orElse(null);
            if (existing != null && existing.getOrgRole() == OrganizationMember.OrgRole.MANAGER) {
                return;
            }
        }
        if (managers >= 1) {
            throw new RuntimeException("Solo puede haber un MANAGER por organización. Elige MEMBER o VIEWER.");
        }
    }

    private Dto.AdminUserRowDto toRowDto(User u) {
        OrganizationMember m = memberRepository.findByUserId(u.getId()).orElse(null);
        String platformRole = u.getRole() != null ? u.getRole().getName() : null;
        String orgRole = m != null ? m.getOrgRole().name() : null;
        String effectiveRoleName = platformRole != null ? platformRole : orgRole;

        Long roleId = null;
        if (effectiveRoleName != null) {
            roleId = roleRepository.findByName(effectiveRoleName).map(Role::getId).orElse(null);
        }

        return Dto.AdminUserRowDto.builder()
                .id(u.getId())
                .email(u.getEmail())
                .name(u.getName())
                .whatsappNumber(u.getWhatsappNumber())
                .roleId(roleId)
                .roleName(effectiveRoleName)
                .platformRole(platformRole)
                .orgRole(orgRole)
                .organizationId(m != null ? m.getOrganization().getId() : null)
                .organizationName(m != null ? m.getOrganization().getName() : null)
                .build();
    }
}
