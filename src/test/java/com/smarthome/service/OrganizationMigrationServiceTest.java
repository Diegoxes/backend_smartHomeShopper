package com.smarthome.service;

import com.smarthome.entity.OrganizationMember;
import com.smarthome.entity.Role;
import com.smarthome.entity.User;
import com.smarthome.repository.*;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationMigrationServiceTest {

    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationMemberRepository memberRepository;
    @Mock OrganizationSettingsRepository settingsRepository;
    @Mock UserRepository userRepository;
    @Mock ProductRepository productRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock RoleRepository roleRepository;
    @InjectMocks OrganizationMigrationService organizationMigrationService;

    @Test
    void migrateIfNeeded_skipsUserWithMembership() {
        User user = TestFixtures.user();
        when(roleRepository.findByName("OWNER")).thenReturn(Optional.empty());
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(memberRepository.findByUserId(TestFixtures.USER_ID))
                .thenReturn(Optional.of(OrganizationMember.builder().build()));

        organizationMigrationService.migrateIfNeeded();

        verify(organizationRepository, never()).save(any());
    }

    @Test
    void migrateIfNeeded_renamesOwnerRole() {
        Role owner = Role.builder().id(1L).name("OWNER").build();
        when(roleRepository.findByName("OWNER")).thenReturn(Optional.of(owner));
        when(userRepository.findAll()).thenReturn(List.of());

        organizationMigrationService.migrateIfNeeded();

        verify(roleRepository).save(argThat(r -> "PLATFORM_OWNER".equals(r.getName())));
    }
}
