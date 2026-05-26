package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Organization;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminOrgService {

    private final OrganizationRepository orgRepo;
    private final OrganizationMemberRepository memberRepo;
    private final OrganizationService organizationService;

    @Transactional(readOnly = true)
    public List<Dto.PendingOrgDto> listByStatus(String statusStr) {
        Organization.Status status;
        try {
            status = Organization.Status.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Estado inválido: " + statusStr + ". Usa PENDING, ACTIVE o REJECTED");
        }

        return orgRepo.findAllByStatusOrderByCreatedAtAsc(status).stream()
                .map(org -> {
                    // Buscar el MANAGER de la organización
                    List<OrganizationMember> members = memberRepo.findAllByOrganizationId(org.getId());
                    OrganizationMember manager = members.stream()
                            .filter(m -> m.getOrgRole() == OrganizationMember.OrgRole.MANAGER)
                            .findFirst()
                            .orElse(members.isEmpty() ? null : members.get(0));

                    return Dto.PendingOrgDto.builder()
                            .orgId(org.getId())
                            .orgName(org.getName())
                            .industry(org.getIndustry())
                            .country(org.getCountry())
                            .orgStatus(org.getStatus().name())
                            .createdAt(org.getCreatedAt())
                            .managerUserId(manager != null ? manager.getUser().getId() : null)
                            .managerName(manager != null ? manager.getUser().getName() : null)
                            .managerEmail(manager != null ? manager.getUser().getEmail() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public void review(String orgId, Dto.OrgApprovalRequest req) {
        if ("APPROVE".equalsIgnoreCase(req.getAction())) {
            organizationService.activateOrg(orgId);
        } else if ("REJECT".equalsIgnoreCase(req.getAction())) {
            organizationService.rejectOrg(orgId);
        } else {
            throw new RuntimeException("Acción inválida: " + req.getAction() + ". Usa APPROVE o REJECT");
        }
    }
}
