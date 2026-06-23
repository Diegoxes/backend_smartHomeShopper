package com.smarthome.service;

import com.smarthome.entity.Organization;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.UserRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformServiceTest {

    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationMemberRepository memberRepository;
    @Mock UserRepository userRepository;
    @InjectMocks PlatformService platformService;

    @Test
    void listOrganizations_mapsMemberCount() {
        Organization org = TestFixtures.organization();
        when(organizationRepository.findAll()).thenReturn(List.of(org));
        when(memberRepository.countByOrganizationId(TestFixtures.ORG_ID)).thenReturn(3L);

        var rows = platformService.listOrganizations();

        assertEquals(1, rows.size());
        assertEquals(3, rows.get(0).getMemberCount());
    }

    @Test
    void updateMaxMembers_notFound_throws() {
        when(organizationRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> platformService.updateMaxMembers("missing", 50));
    }

    @Test
    void updateMaxMembers_success() {
        Organization org = TestFixtures.organization();
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(org));
        when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.countByOrganizationId(TestFixtures.ORG_ID)).thenReturn(2L);

        var row = platformService.updateMaxMembers(TestFixtures.ORG_ID, 50);

        assertEquals(50, row.getMaxMembers());
    }
}
