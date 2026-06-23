package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.*;
import com.smarthome.repository.*;
import com.smarthome.security.SessionPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationService {

    private final OrganizationContextService orgContext;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final JwtService jwtService;
    private final UserPermissionService userPermissionService;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;
    private final MeasureUnitService measureUnitService;

    public Dto.AuthResponse onboard(Dto.OnboardingRequest req) {
        String userId = orgContext.requireUserId();
        if (memberRepository.findByUserId(userId).isPresent()) {
            throw new RuntimeException("Ya tienes una organización registrada");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Org pendiente de aprobación del PLATFORM_OWNER; almacén y seeds se crean en activateOrg()
        Organization org = organizationRepository.save(Organization.builder()
                .name(req.getName().trim())
                .industry(req.getIndustry())
                .currency(req.getCurrency() != null ? req.getCurrency() : "MXN")
                .country(req.getCountry())
                .timezone(req.getTimezone() != null ? req.getTimezone() : "America/Mexico_City")
                .maxMembers(20)
                .status(Organization.Status.PENDING)
                .build());

        settingsRepository.save(OrganizationSettings.builder()
                .organization(org)
                .expiryAlertDays(7)
                .predictionHorizonDays(30)
                .build());

        memberRepository.save(OrganizationMember.builder()
                .organization(org)
                .user(user)
                .orgRole(OrganizationMember.OrgRole.MANAGER)
                .build());

        String token = jwtService.generate(userId, user.getEmail(), null, org.getId(), "MANAGER");
        return Dto.AuthResponse.builder()
                .token(token)
                .userId(userId)
                .name(user.getName())
                .email(user.getEmail())
                .role("MANAGER")
                .orgRole("MANAGER")
                .orgId(org.getId())
                .orgStatus("PENDING")
                .needsOnboarding(false)
                .permissions(List.of())
                .build();
    }

    /** Activa la organización una vez aprobada: crea el almacén y habilita permisos. */
    public void activateOrg(String orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organización no encontrada"));
        org.setStatus(Organization.Status.ACTIVE);
        organizationRepository.save(org);

        boolean hasWarehouse = !warehouseRepository.findByOrganizationIdOrderByNameAsc(orgId).isEmpty();
        if (!hasWarehouse) {
            warehouseRepository.save(Warehouse.builder()
                    .organization(org)
                    .name("Principal")
                    .isDefault(true)
                    .build());
        }
        categoryService.seedDefaultsIfEmpty(orgId);
        measureUnitService.seedDefaultsIfEmpty(orgId);
    }

    public void rejectOrg(String orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organización no encontrada"));
        org.setStatus(Organization.Status.REJECTED);
        organizationRepository.save(org);
    }

    @Transactional(readOnly = true)
    public Dto.OrganizationDto getMyOrganization() {
        String orgId = orgContext.requireOrgId();
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        return toDto(org);
    }

    public Dto.OrganizationDto updateMyOrganization(Dto.UpdateOrganizationRequest req) {
        requireManager();
        String orgId = orgContext.requireActiveOrgId();
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        if (req.getName() != null) org.setName(req.getName().trim());
        if (req.getIndustry() != null) org.setIndustry(req.getIndustry());
        if (req.getCurrency() != null) org.setCurrency(req.getCurrency());
        if (req.getCountry() != null) org.setCountry(req.getCountry());
        if (req.getTimezone() != null) org.setTimezone(req.getTimezone());
        return toDto(organizationRepository.save(org));
    }

    @Transactional(readOnly = true)
    public List<Dto.OrgMemberDto> listMembers() {
        requireManager();
        String orgId = orgContext.requireActiveOrgId();
        return memberRepository.findAllByOrganizationId(orgId).stream()
                .map(this::toMemberDto)
                .collect(Collectors.toList());
    }

    public Dto.OrgMemberDto addMember(Dto.CreateOrgMemberRequest req) {
        requireManager();
        String orgId = orgContext.requireActiveOrgId();
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        long count = memberRepository.countByOrganizationId(orgId);
        if (count >= org.getMaxMembers()) {
            throw new RuntimeException("Límite de miembros alcanzado (" + org.getMaxMembers() + ")");
        }

        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User nu = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(req.getPassword()))
                    .name(req.getName().trim())
                    .whatsappNumber(req.getWhatsappNumber())
                    .role(null)
                    .build();
            return userRepository.save(nu);
        });

        if (memberRepository.findByUserId(user.getId()).isPresent()) {
            throw new RuntimeException("El usuario ya pertenece a una organización");
        }

        OrganizationMember.OrgRole role = OrganizationMember.OrgRole.valueOf(req.getOrgRole());
        if (role == OrganizationMember.OrgRole.MANAGER) {
            long managers = memberRepository.countByOrganizationIdAndOrgRole(orgId, OrganizationMember.OrgRole.MANAGER);
            if (managers >= 1) throw new RuntimeException("Solo puede haber un MANAGER por organización");
        }

        OrganizationMember member = memberRepository.save(OrganizationMember.builder()
                .organization(org)
                .user(user)
                .orgRole(role)
                .build());
        return toMemberDto(member);
    }

    public Dto.OrgMemberDto updateMember(String memberId, Dto.UpdateOrgMemberRequest req) {
        requireManager();
        String orgId = orgContext.requireActiveOrgId();
        OrganizationMember member = memberRepository.findByIdAndOrganizationId(memberId, orgId)
                .orElseThrow(() -> new RuntimeException("Miembro no encontrado"));
        if (req.getOrgRole() != null) {
            OrganizationMember.OrgRole role = OrganizationMember.OrgRole.valueOf(req.getOrgRole());
            if (role == OrganizationMember.OrgRole.MANAGER && member.getOrgRole() != OrganizationMember.OrgRole.MANAGER) {
                long managers = memberRepository.countByOrganizationIdAndOrgRole(orgId, OrganizationMember.OrgRole.MANAGER);
                if (managers >= 1) throw new RuntimeException("Solo puede haber un MANAGER por organización");
            }
            member.setOrgRole(role);
        }
        if (req.getName() != null) member.getUser().setName(req.getName().trim());
        return toMemberDto(memberRepository.save(member));
    }

    public void removeMember(String memberId) {
        requireManager();
        String orgId = orgContext.requireActiveOrgId();
        OrganizationMember member = memberRepository.findByIdAndOrganizationId(memberId, orgId)
                .orElseThrow(() -> new RuntimeException("Miembro no encontrado"));
        if (member.getOrgRole() == OrganizationMember.OrgRole.MANAGER) {
            throw new RuntimeException("No puedes eliminar al MANAGER de la organización");
        }
        memberRepository.delete(member);
    }

    private void requireManager() {
        SessionPrincipal sp = orgContext.requireSession();
        if (!"MANAGER".equals(sp.orgRole()) && !sp.isPlatformOwner()) {
            throw new AccessDeniedException("Solo el MANAGER puede gestionar el equipo");
        }
    }

    private Dto.OrganizationDto toDto(Organization org) {
        OrganizationSettings s = org.getSettings();
        return Dto.OrganizationDto.builder()
                .id(org.getId())
                .name(org.getName())
                .industry(org.getIndustry())
                .currency(org.getCurrency())
                .country(org.getCountry())
                .timezone(org.getTimezone())
                .maxMembers(org.getMaxMembers())
                .expiryAlertDays(s != null ? s.getExpiryAlertDays() : 7)
                .predictionHorizonDays(s != null ? s.getPredictionHorizonDays() : 30)
                .build();
    }

    private Dto.OrgMemberDto toMemberDto(OrganizationMember m) {
        return Dto.OrgMemberDto.builder()
                .id(m.getId())
                .userId(m.getUser().getId())
                .email(m.getUser().getEmail())
                .name(m.getUser().getName())
                .orgRole(m.getOrgRole().name())
                .whatsappNumber(m.getUser().getWhatsappNumber())
                .build();
    }
}
