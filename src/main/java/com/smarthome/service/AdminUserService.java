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

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public List<Dto.AdminUserRowDto> listUsers() {
        return userRepository.findAllWithRole().stream()
                .map(u -> Dto.AdminUserRowDto.builder()
                        .id(u.getId())
                        .email(u.getEmail())
                        .name(u.getName())
                        .whatsappNumber(u.getWhatsappNumber())
                        .roleId(u.getRole().getId())
                        .roleName(u.getRole().getName())
                        .build())
                .toList();
    }

    @Transactional
    public Dto.AdminUserRowDto createUser(Dto.AdminCreateUserRequest req) {
        String email = normalizeEmail(req.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email))
            throw new RuntimeException("Email already registered");

        Role role = roleRepository.findById(req.getRoleId())
                .orElseThrow(() -> new RuntimeException("Rol not found"));

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName().trim())
                .whatsappNumber(req.getWhatsappNumber() != null ? req.getWhatsappNumber().trim() : null)
                .role(role)
                .build();
        userRepository.save(user);

        return Dto.AdminUserRowDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .whatsappNumber(user.getWhatsappNumber())
                .roleId(role.getId())
                .roleName(role.getName())
                .build();
    }

    @Transactional
    public void updateUserRole(String userId, Dto.AdminUpdateUserRoleRequest req) {
        Role newRole = roleRepository.findById(req.getRoleId())
                .orElseThrow(() -> new RuntimeException("Rol not found"));

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("OWNER".equals(target.getRole().getName()) && !"OWNER".equals(newRole.getName())) {
            if (userRepository.countByRole_Name("OWNER") <= 1) {
                throw new RuntimeException("Forbidden: debe existir al menos un usuario con rol OWNER");
            }
        }

        target.setRole(newRole);
        userRepository.save(target);
    }
}
