package com.smarthome.service;

import com.smarthome.entity.Role;
import com.smarthome.repository.AppModuleRepository;
import com.smarthome.repository.RoleModuleRepository;
import com.smarthome.repository.RoleRepository;
import com.smarthome.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RbacSeedServiceTest {

    @Mock RoleRepository roleRepository;
    @Mock AppModuleRepository moduleRepository;
    @Mock RoleModuleRepository roleModuleRepository;
    @Mock UserRepository userRepository;
    @InjectMocks RbacSeedService rbacSeedService;

    @Test
    void ensureSeeded_whenRolesExist_skipsFullSeed() {
        when(roleRepository.count()).thenReturn(4L);
        when(roleModuleRepository.count()).thenReturn(10L);
        Role member = Role.builder().id(1L).name("MEMBER").build();
        when(roleRepository.findByName("MEMBER")).thenReturn(Optional.of(member));
        when(userRepository.updateRoleIdWhereNull(1L)).thenReturn(0);

        rbacSeedService.ensureSeeded();

        verify(moduleRepository, never()).save(any());
    }

    @Test
    void ensureSeeded_whenEmpty_seedsRolesAndModules() {
        when(roleRepository.count()).thenReturn(0L);
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(roleRepository.findByName(anyString())).thenAnswer(inv ->
                Optional.of(Role.builder().id(1L).name(inv.getArgument(0)).build()));
        when(moduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(moduleRepository.findByKey(anyString())).thenReturn(Optional.empty());
        lenient().when(userRepository.updateRoleIdWhereNull(anyLong())).thenReturn(0);

        rbacSeedService.ensureSeeded();

        verify(roleRepository, atLeast(4)).save(any(Role.class));
        verify(moduleRepository, atLeast(4)).save(any());
    }
}
