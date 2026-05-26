package com.smarthome.repository;

import com.smarthome.entity.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, String> {

    Optional<StockLevel> findByProductIdAndWarehouseId(String productId, String warehouseId);

    List<StockLevel> findByProductId(String productId);

    @Query("SELECT sl FROM StockLevel sl JOIN sl.product p WHERE p.organization.id = :orgId AND sl.warehouse.id = :whId")
    List<StockLevel> findByOrgAndWarehouse(@Param("orgId") String orgId, @Param("whId") String warehouseId);
}
