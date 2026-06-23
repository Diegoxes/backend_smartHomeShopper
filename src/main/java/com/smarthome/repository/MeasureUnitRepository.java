package com.smarthome.repository;

import com.smarthome.entity.MeasureUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeasureUnitRepository extends JpaRepository<MeasureUnit, String> {

    List<MeasureUnit> findByOrganizationIdAndActiveTrueOrderByBaseUnitDescNameAsc(String organizationId);

    List<MeasureUnit> findByOrganizationIdOrderByBaseUnitDescNameAsc(String organizationId);

    Optional<MeasureUnit> findByIdAndOrganizationId(String id, String organizationId);

    Optional<MeasureUnit> findByOrganizationIdAndCode(String organizationId, String code);

    Optional<MeasureUnit> findByOrganizationIdAndBaseUnitTrue(String organizationId);

    boolean existsByOrganizationIdAndCode(String organizationId, String code);
}
