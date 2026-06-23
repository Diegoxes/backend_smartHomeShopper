package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.AppModule;
import com.smarthome.entity.Role;
import com.smarthome.entity.RoleModule;
import com.smarthome.repository.RoleRepository;
import com.smarthome.repository.UserRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPermissionServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @InjectMocks UserPermissionService userPermissionService;

    @Test
    void loadAuthorities_platformOwner() {
        var authorities = userPermissionService.loadAuthorities(
                new com.smarthome.security.SessionPrincipal("u1", null, null, "PLATFORM_OWNER"));
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_OWNER")));
    }

    @Test
    void loadAuthorities_orgRoleWithModules() {
        AppModule mod = TestFixtures.inventoryModule();
        RoleModule rm = TestFixtures.roleModule(mod, true, true);
        Role role = Role.builder().name("MANAGER").roleModules(List.of(rm)).build();
        when(roleRepository.findByNameWithRoleModules("MANAGER")).thenReturn(Optional.of(role));

        List<GrantedAuthority> authorities = userPermissionService.loadAuthorities(TestFixtures.orgMemberSession());
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("INVENTORY_READ")));
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("INVENTORY_CREATE")));
    }

    @Test
    void modulePermissionsForSession_platformOwnerEmpty() {
        var perms = userPermissionService.modulePermissionsForSession(
                new com.smarthome.security.SessionPrincipal("u1", null, null, "PLATFORM_OWNER"));
        assertTrue(perms.isEmpty());
    }

    @Test
    void modulePermissionsForSession_orgRole() {
        AppModule mod = TestFixtures.inventoryModule();
        RoleModule rm = TestFixtures.roleModule(mod, true, false);
        Role role = Role.builder().name("MANAGER").roleModules(List.of(rm)).build();
        when(roleRepository.findByNameWithRoleModules("MANAGER")).thenReturn(Optional.of(role));

        List<Dto.ModulePermissionDto> perms = userPermissionService.modulePermissionsForSession(TestFixtures.orgMemberSession());
        assertEquals(1, perms.size());
        assertEquals("INVENTORY", perms.get(0).getKey());
        assertTrue(perms.get(0).isCanRead());
    }
}
