package com.smarthome.repository;

import com.smarthome.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, String> {

    List<Warehouse> findByOrganizationIdOrderByNameAsc(String organizationId);

    @Query("SELECT w FROM Warehouse w WHERE w.organization.id = :orgId AND w.isDefault = true")
    Optional<Warehouse> findDefaultByOrganizationId(@Param("orgId") String orgId);

    Optional<Warehouse> findByIdAndOrganizationId(String id, String organizationId);
}
