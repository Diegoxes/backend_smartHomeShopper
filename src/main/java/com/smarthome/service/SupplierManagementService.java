package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Organization;
import com.smarthome.entity.Supplier;
import com.smarthome.entity.User;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.SupplierRepository;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierManagementService {

    private final SupplierRepository supplierRepo;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationContextService orgContext;

    @Transactional(readOnly = true)
    public List<Dto.SupplierDto> list(String userId) {
        return supplierRepo.listAllForOrganization(orgContext.requireActiveOrgId()).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public Dto.SupplierDto create(String userId, Dto.CreateSupplierRequest req) {
        String orgId = orgContext.requireActiveOrgId();
        User owner = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Organization org = organizationRepository.findById(orgId).orElseThrow(() -> new RuntimeException("Organization not found"));
        Supplier s = Supplier.builder()
                .organization(org)
                .user(owner)
                .name(req.getName().trim())
                .phone(req.getPhone())
                .leadTimeDays(req.getLeadTimeDays())
                .notes(req.getNotes())
                .build();
        return toDto(supplierRepo.save(s));
    }

    @Transactional
    public Dto.SupplierDto update(String userId, String id, Dto.UpdateSupplierRequest req) {
        String orgId = orgContext.requireActiveOrgId();
        Supplier s = supplierRepo.findOwned(id, orgId).orElseThrow(() -> new RuntimeException("Supplier not found"));
        if (req.getName() != null) s.setName(req.getName().trim());
        if (req.getPhone() != null) s.setPhone(req.getPhone());
        if (req.getLeadTimeDays() != null) s.setLeadTimeDays(req.getLeadTimeDays());
        if (req.getNotes() != null) s.setNotes(req.getNotes());
        return toDto(supplierRepo.save(s));
    }

    @Transactional
    public void delete(String userId, String id) {
        Supplier s = supplierRepo.findOwned(id, orgContext.requireActiveOrgId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
        supplierRepo.delete(s);
    }

    private Dto.SupplierDto toDto(Supplier s) {
        return Dto.SupplierDto.builder()
                .id(s.getId())
                .name(s.getName())
                .phone(s.getPhone())
                .leadTimeDays(s.getLeadTimeDays())
                .notes(s.getNotes())
                .build();
    }
}
