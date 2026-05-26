package com.smarthome.repository;

import com.smarthome.entity.InventoryLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, String> {

    @Query("""
            SELECT l FROM InventoryLot l
            WHERE l.product.id = :productId AND l.warehouse.id = :warehouseId AND l.quantity > 0
            ORDER BY l.receivedAt ASC, l.expiryDate ASC NULLS LAST
            """)
    List<InventoryLot> findAvailableFifo(@Param("productId") String productId, @Param("warehouseId") String warehouseId);

    List<InventoryLot> findByProductIdOrderByReceivedAtAsc(String productId);
}
