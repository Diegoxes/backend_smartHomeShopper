package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Organization;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.entity.Warehouse;
import com.smarthome.repository.*;
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
class OrganizationServiceTest {

    @Mock OrganizationContextService orgContext;
    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationMemberRepository memberRepository;
    @Mock OrganizationSettingsRepository settingsRepository;
    @Mock UserRepository userRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock JwtService jwtService;
    @Mock UserPermissionService userPermissionService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CategoryService categoryService;
    @Mock MeasureUnitService measureUnitService;
    @InjectMocks OrganizationService organizationService;

    @Test
    void onboard_alreadyHasOrg_throws() {
        when(orgContext.requireUserId()).thenReturn(TestFixtures.USER_ID);
        when(memberRepository.findByUserId(TestFixtures.USER_ID))
                .thenReturn(Optional.of(OrganizationMember.builder().build()));

        Dto.OnboardingRequest req = new Dto.OnboardingRequest();
        req.setName("Mi Negocio");

        assertThrows(RuntimeException.class, () -> organizationService.onboard(req));
    }

    @Test
    void onboard_createsPendingOrgAndReturnsToken() {
        when(orgContext.requireUserId()).thenReturn(TestFixtures.USER_ID);
        when(memberRepository.findByUserId(TestFixtures.USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(TestFixtures.user()));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> {
            Organization o = inv.getArgument(0);
            o.setId(TestFixtures.ORG_ID);
            assertEquals(Organization.Status.PENDING, o.getStatus());
            return o;
        });
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generate(any(), any(), any(), any(), any())).thenReturn("jwt-onboard");

        Dto.OnboardingRequest req = new Dto.OnboardingRequest();
        req.setName("Mi Negocio");
        req.setIndustry("retail");

        Dto.AuthResponse response = organizationService.onboard(req);

        assertEquals("jwt-onboard", response.getToken());
        assertEquals(TestFixtures.ORG_ID, response.getOrgId());
        assertEquals("PENDING", response.getOrgStatus());
        assertTrue(response.getPermissions().isEmpty());
        verify(warehouseRepository, never()).save(any());
        verify(categoryService, never()).seedDefaultsIfEmpty(any());
        verify(measureUnitService, never()).seedDefaultsIfEmpty(any());
    }

    @Test
    void addMember_pendingOrg_throwsAccessDenied() {
        when(orgContext.requireSession()).thenReturn(TestFixtures.orgMemberSession());
        when(orgContext.requireActiveOrgId()).thenThrow(new org.springframework.security.access.AccessDeniedException("pendiente"));

        Dto.CreateOrgMemberRequest req = new Dto.CreateOrgMemberRequest();
        req.setEmail("member@test.com");
        req.setPassword("secret123");
        req.setName("Member");
        req.setOrgRole("MEMBER");

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> organizationService.addMember(req));
    }

    @Test
    void rejectOrg_setsStatusRejected() {
        Organization org = TestFixtures.organization();
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(org));

        organizationService.rejectOrg(TestFixtures.ORG_ID);

        assertEquals(Organization.Status.REJECTED, org.getStatus());
        verify(organizationRepository).save(org);
    }

    @Test
    void activateOrg_createsWarehouseIfMissing() {
        Organization org = TestFixtures.organization();
        org.setStatus(Organization.Status.PENDING);
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(org));
        when(warehouseRepository.findByOrganizationIdOrderByNameAsc(TestFixtures.ORG_ID)).thenReturn(List.of());

        organizationService.activateOrg(TestFixtures.ORG_ID);

        assertEquals(Organization.Status.ACTIVE, org.getStatus());
        verify(warehouseRepository).save(any(Warehouse.class));
    }
}
