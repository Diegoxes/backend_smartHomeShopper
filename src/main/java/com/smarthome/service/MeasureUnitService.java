package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.MeasureUnit;
import com.smarthome.entity.Organization;
import com.smarthome.repository.MeasureUnitRepository;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.ProductUomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeasureUnitService {

    private record DefaultUnit(String code, String name, boolean baseUnit) {}

    private static final List<DefaultUnit> DEFAULTS = List.of(
            new DefaultUnit("UNIT", "Unidad", true),
            new DefaultUnit("BOX", "Caja", false),
            new DefaultUnit("PACK", "Pack", false),
            new DefaultUnit("DOZEN", "Docena", false)
    );

    private final MeasureUnitRepository measureUnitRepository;
    private final OrganizationRepository organizationRepository;
    private final ProductUomRepository productUomRepository;
    private final OrganizationContextService orgContext;

    @Transactional(readOnly = true)
    public List<Dto.MeasureUnitDto> listActive() {
        seedDefaultsIfEmpty(orgContext.requireActiveOrgId());
        return measureUnitRepository.findByOrganizationIdAndActiveTrueOrderByBaseUnitDescNameAsc(
                        orgContext.requireActiveOrgId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public Dto.MeasureUnitDto create(Dto.CreateMeasureUnitRequest req) {
        String orgId = orgContext.requireActiveOrgId();
        if (req.isBaseUnit()) {
            throw new IllegalArgumentException("No se puede crear otra unidad base");
        }
        String code = req.getCode().trim().toUpperCase();
        if (measureUnitRepository.existsByOrganizationIdAndCode(orgId, code)) {
            throw new IllegalArgumentException("Ya existe una unidad con ese código");
        }
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organización no encontrada"));
        MeasureUnit mu = measureUnitRepository.save(MeasureUnit.builder()
                .organization(org)
                .code(code)
                .name(req.getName().trim())
                .baseUnit(false)
                .active(true)
                .build());
        return toDto(mu);
    }

    @Transactional
    public Dto.MeasureUnitDto update(String id, Dto.UpdateMeasureUnitRequest req) {
        String orgId = orgContext.requireActiveOrgId();
        MeasureUnit mu = owned(id, orgId);
        if (mu.isBaseUnit() && req.getActive() != null && !req.getActive()) {
            throw new IllegalArgumentException("No se puede desactivar la unidad base");
        }
        if (req.getName() != null && !req.getName().isBlank()) mu.setName(req.getName().trim());
        if (req.getActive() != null) mu.setActive(req.getActive());
        return toDto(measureUnitRepository.save(mu));
    }

    @Transactional
    public void delete(String id) {
        String orgId = orgContext.requireActiveOrgId();
        MeasureUnit mu = owned(id, orgId);
        if (mu.isBaseUnit()) {
            throw new IllegalArgumentException("No se puede eliminar la unidad base");
        }
        if (productUomRepository.existsByMeasureUnitId(id)) {
            mu.setActive(false);
            measureUnitRepository.save(mu);
            return;
        }
        measureUnitRepository.delete(mu);
    }

    @Transactional
    public void seedDefaultsIfEmpty(String organizationId) {
        if (!measureUnitRepository.findByOrganizationIdOrderByBaseUnitDescNameAsc(organizationId).isEmpty()) {
            return;
        }
        Organization org = organizationRepository.findById(organizationId).orElse(null);
        if (org == null) return;
        for (DefaultUnit def : DEFAULTS) {
            if (!measureUnitRepository.existsByOrganizationIdAndCode(organizationId, def.code())) {
                measureUnitRepository.save(MeasureUnit.builder()
                        .organization(org)
                        .code(def.code())
                        .name(def.name())
                        .baseUnit(def.baseUnit())
                        .active(true)
                        .build());
            }
        }
    }

    @Transactional(readOnly = true)
    public MeasureUnit requireBaseUnit(String orgId) {
        seedDefaultsIfEmpty(orgId);
        return measureUnitRepository.findByOrganizationIdAndBaseUnitTrue(orgId)
                .orElseThrow(() -> new IllegalStateException("Unidad base no configurada"));
    }

    @Transactional(readOnly = true)
    public MeasureUnit ownedUnit(String measureUnitId, String orgId) {
        return measureUnitRepository.findByIdAndOrganizationId(measureUnitId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Unidad de medida no encontrada"));
    }

    @Transactional(readOnly = true)
    public String boxUnitId(String organizationId) {
        seedDefaultsIfEmpty(organizationId);
        return measureUnitRepository.findByOrganizationIdAndCode(organizationId, "BOX")
                .map(MeasureUnit::getId)
                .orElse(null);
    }

    private MeasureUnit owned(String id, String orgId) {
        return measureUnitRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Unidad de medida no encontrada"));
    }

    private Dto.MeasureUnitDto toDto(MeasureUnit mu) {
        return Dto.MeasureUnitDto.builder()
                .id(mu.getId())
                .code(mu.getCode())
                .name(mu.getName())
                .baseUnit(mu.isBaseUnit())
                .active(mu.isActive())
                .build();
    }
}
