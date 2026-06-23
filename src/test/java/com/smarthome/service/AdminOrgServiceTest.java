package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Organization;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrgServiceTest {

    @Mock OrganizationRepository orgRepo;
    @Mock OrganizationMemberRepository memberRepo;
    @Mock OrganizationService organizationService;
    @InjectMocks AdminOrgService adminOrgService;

    @Test
    void listByStatus_invalidStatus_throws() {
        assertThrows(RuntimeException.class, () -> adminOrgService.listByStatus("INVALID"));
    }

    @Test
    void listByStatus_mapsPendingOrgs() {
        Organization org = TestFixtures.organization();
        org.setStatus(Organization.Status.PENDING);
        OrganizationMember member = OrganizationMember.builder()
                .organization(org)
                .user(TestFixtures.user())
                .orgRole(OrganizationMember.OrgRole.MANAGER)
                .build();
        when(orgRepo.findAllByStatusOrderByCreatedAtAsc(Organization.Status.PENDING)).thenReturn(List.of(org));
        when(memberRepo.findAllByOrganizationId(TestFixtures.ORG_ID)).thenReturn(List.of(member));

        List<Dto.PendingOrgDto> result = adminOrgService.listByStatus("PENDING");

        assertEquals(1, result.size());
        assertEquals(TestFixtures.ORG_ID, result.get(0).getOrgId());
    }

    @Test
    void review_approve_delegatesToOrganizationService() {
        Dto.OrgApprovalRequest req = new Dto.OrgApprovalRequest();
        req.setAction("APPROVE");

        adminOrgService.review(TestFixtures.ORG_ID, req);

        verify(organizationService).activateOrg(TestFixtures.ORG_ID);
    }

    @Test
    void review_invalidAction_throws() {
        Dto.OrgApprovalRequest req = new Dto.OrgApprovalRequest();
        req.setAction("MAYBE");

        assertThrows(RuntimeException.class, () -> adminOrgService.review(TestFixtures.ORG_ID, req));
    }
}
