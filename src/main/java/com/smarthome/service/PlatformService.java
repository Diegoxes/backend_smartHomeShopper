package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Organization;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;

    public List<Dto.PlatformOrganizationRowDto> listOrganizations() {
        return organizationRepository.findAll().stream()
                .map(org -> Dto.PlatformOrganizationRowDto.builder()
                        .id(org.getId())
                        .name(org.getName())
                        .industry(org.getIndustry())
                        .memberCount((int) memberRepository.countByOrganizationId(org.getId()))
                        .maxMembers(org.getMaxMembers())
                        .createdAt(org.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public List<Dto.PlatformUserRowDto> listUsers() {
        return userRepository.findAll().stream().map(u -> {
            OrganizationMember m = memberRepository.findByUserId(u.getId()).orElse(null);
            return Dto.PlatformUserRowDto.builder()
                    .id(u.getId())
                    .email(u.getEmail())
                    .name(u.getName())
                    .orgName(m != null ? m.getOrganization().getName() : null)
                    .orgRole(m != null ? m.getOrgRole().name() : null)
                    .platformRole(u.getRole() != null ? u.getRole().getName() : null)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public Dto.PlatformOrganizationRowDto updateMaxMembers(String orgId, int maxMembers) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        org.setMaxMembers(maxMembers);
        organizationRepository.save(org);
        return Dto.PlatformOrganizationRowDto.builder()
                .id(org.getId())
                .name(org.getName())
                .industry(org.getIndustry())
                .memberCount((int) memberRepository.countByOrganizationId(org.getId()))
                .maxMembers(org.getMaxMembers())
                .createdAt(org.getCreatedAt())
                .build();
    }
}
