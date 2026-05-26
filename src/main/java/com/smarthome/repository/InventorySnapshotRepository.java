package com.smarthome.repository;

import com.smarthome.entity.InventorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface InventorySnapshotRepository extends JpaRepository<InventorySnapshot, String> {

    List<InventorySnapshot> findByOrganizationIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            String organizationId, LocalDate from, LocalDate to);
}
