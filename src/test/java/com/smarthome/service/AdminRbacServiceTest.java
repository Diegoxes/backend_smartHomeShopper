package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.AppModule;
import com.smarthome.entity.Role;
import com.smarthome.entity.RoleModule;
import com.smarthome.repository.AppModuleRepository;
import com.smarthome.repository.RoleModuleRepository;
import com.smarthome.repository.RoleRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRbacServiceTest {

    @Mock RoleRepository roleRepository;
    @Mock AppModuleRepository moduleRepository;
    @Mock RoleModuleRepository roleModuleRepository;
    @InjectMocks AdminRbacService adminRbacService;

    @Test
    void listRoles_returnsAll() {
        when(roleRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(Role.builder().id(1L).name("MANAGER").build()));
        assertEquals(1, adminRbacService.listRoles().size());
    }

    @Test
    void getMatrix_buildsCells() {
        AppModule mod = TestFixtures.inventoryModule();
        RoleModule rm = TestFixtures.roleModule(mod, true, false);
        Role role = Role.builder().id(1L).name("MANAGER").roleModules(List.of(rm)).build();
        when(roleRepository.findAllWithRoleModules()).thenReturn(List.of(role));
        when(moduleRepository.findAll(any(Sort.class))).thenReturn(List.of(mod));

        Dto.RbacMatrixResponse matrix = adminRbacService.getMatrix();

        assertEquals(1, matrix.getRoles().size());
        assertEquals(1, matrix.getPermissions().size());
    }

    @Test
    void updatePermissionsBatch_missingCell_throws() {
        when(roleModuleRepository.findByRole_IdAndModule_Id(1L, 1L)).thenReturn(Optional.empty());
        Dto.RoleModuleCellDto cell = Dto.RoleModuleCellDto.builder()
                .roleId(1L).moduleId(1L).canRead(true).build();

        assertThrows(RuntimeException.class,
                () -> adminRbacService.updatePermissionsBatch(List.of(cell)));
    }
}
