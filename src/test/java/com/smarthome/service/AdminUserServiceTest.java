package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Role;
import com.smarthome.entity.User;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.RoleRepository;
import com.smarthome.repository.UserRepository;
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
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationMemberRepository memberRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AdminUserService adminUserService;

    @Test
    void createUser_duplicateEmail_throws() {
        Dto.AdminCreateUserRequest req = new Dto.AdminCreateUserRequest();
        req.setEmail("test@example.com");
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> adminUserService.createUser(req));
    }

    @Test
    void createUser_platformOwnerWithoutOrg_success() {
        Dto.AdminCreateUserRequest req = new Dto.AdminCreateUserRequest();
        req.setEmail("owner@example.com");
        req.setPassword("secret123");
        req.setName("Owner");
        req.setRoleId(1L);
        Role poRole = Role.builder().id(1L).name("PLATFORM_OWNER").build();

        when(userRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(poRole));
        when(passwordEncoder.encode("secret123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u-new");
            return u;
        });
        when(userRepository.findByIdWithRbac("u-new")).thenReturn(Optional.of(
                User.builder().id("u-new").email("owner@example.com").name("Owner").role(poRole).build()));

        Dto.AdminUserRowDto row = adminUserService.createUser(req);

        assertEquals("owner@example.com", row.getEmail());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void listUsers_returnsRows() {
        when(userRepository.findAllWithRole()).thenReturn(List.of(TestFixtures.user()));
        assertEquals(1, adminUserService.listUsers().size());
    }
}
