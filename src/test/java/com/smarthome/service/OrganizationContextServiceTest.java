package com.smarthome.service;

import com.smarthome.entity.Organization;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.support.SecurityContextTestHelper;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationContextServiceTest {

    @Mock OrganizationRepository organizationRepository;

    @AfterEach
    void tearDown() {
        SecurityContextTestHelper.clear();
    }

    private OrganizationContextService service() {
        return new OrganizationContextService(organizationRepository);
    }

    @Test
    void requireSession_withoutAuth_throwsAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> service().requireSession());
    }

    @Test
    void requireOrgId_withoutOrgId_throwsAccessDenied() {
        SecurityContextTestHelper.setPlatformOwner(TestFixtures.USER_ID);
        assertThrows(AccessDeniedException.class, () -> service().requireOrgId());
    }

    @Test
    void requireOrgId_withOrg_returnsOrgId() {
        SecurityContextTestHelper.setOrgMember(TestFixtures.USER_ID, TestFixtures.ORG_ID);
        assertEquals(TestFixtures.ORG_ID, service().requireOrgId());
    }

    @Test
    void requireActiveOrgId_pendingOrg_throwsAccessDenied() {
        SecurityContextTestHelper.setOrgMember(TestFixtures.USER_ID, TestFixtures.ORG_ID);
        Organization pending = TestFixtures.organization();
        pending.setStatus(Organization.Status.PENDING);
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(pending));

        assertThrows(AccessDeniedException.class, () -> service().requireActiveOrgId());
    }

    @Test
    void requireActiveOrgId_activeOrg_returnsOrgId() {
        SecurityContextTestHelper.setOrgMember(TestFixtures.USER_ID, TestFixtures.ORG_ID);
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(TestFixtures.organization()));

        assertEquals(TestFixtures.ORG_ID, service().requireActiveOrgId());
    }

    @Test
    void isPlatformOwner_returnsTrueForPlatformOwner() {
        SecurityContextTestHelper.setPlatformOwner(TestFixtures.USER_ID);
        assertTrue(service().isPlatformOwner());
    }
}
