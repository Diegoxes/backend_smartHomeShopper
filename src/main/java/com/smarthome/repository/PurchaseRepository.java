package com.smarthome.repository;

import com.smarthome.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, String> {

    @Query("""
            SELECT pu FROM Purchase pu
            JOIN pu.product p
            WHERE p.organization.id = :orgId
              AND (:productId IS NULL OR p.id = :productId)
              AND pu.purchasedAt >= :from
              AND pu.purchasedAt <= :to
            ORDER BY pu.purchasedAt DESC
            """)
    List<Purchase> findFiltered(
            @Param("orgId") String orgId,
            @Param("productId") String productId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT COALESCE(SUM(pu.totalAmount), 0)
            FROM Purchase pu
            JOIN pu.product p
            WHERE p.organization.id = :orgId
              AND (:productId IS NULL OR p.id = :productId)
              AND pu.purchasedAt >= :from
              AND pu.purchasedAt <= :to
            """)
    BigDecimal sumTotalInRange(
            @Param("orgId") String orgId,
            @Param("productId") String productId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    Optional<Purchase> findFirstByProductIdOrderByPurchasedAtDesc(String productId);

    @Query("""
            SELECT pu.supplier.id, pu.supplier.name, COALESCE(SUM(pu.totalAmount), 0)
            FROM Purchase pu JOIN pu.product p
            WHERE p.organization.id = :orgId AND pu.purchasedAt >= :from AND pu.purchasedAt <= :to
            GROUP BY pu.supplier.id, pu.supplier.name
            """)
    List<Object[]> sumBySupplier(
            @Param("orgId") String orgId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
