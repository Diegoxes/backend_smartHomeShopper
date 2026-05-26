package com.smarthome.repository;

import com.smarthome.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, String> {

    @Query("SELECT s FROM Supplier s WHERE s.organization.id = :orgId ORDER BY s.name ASC")
    List<Supplier> listAllForOrganization(@Param("orgId") String orgId);

    @Query("SELECT s FROM Supplier s WHERE s.id = :id AND s.organization.id = :orgId")
    Optional<Supplier> findOwned(@Param("id") String id, @Param("orgId") String orgId);
}
