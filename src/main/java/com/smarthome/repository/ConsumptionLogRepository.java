package com.smarthome.repository;

import com.smarthome.entity.ConsumptionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ConsumptionLogRepository extends JpaRepository<ConsumptionLog, String> {

    List<ConsumptionLog> findByProductIdOrderByCreatedAtDesc(String productId);

    @Query("""
            SELECT DISTINCT c.product.id FROM ConsumptionLog c
            JOIN c.product p
            WHERE p.organization.id = :orgId AND c.createdAt >= :since
              AND c.actionType = :actionType
            """)
    List<String> findDistinctConsumedProductIdsSince(
            @Param("orgId") String orgId,
            @Param("since") LocalDateTime since,
            @Param("actionType") ConsumptionLog.ActionType actionType
    );

    @Query("""
            SELECT c FROM ConsumptionLog c
            JOIN FETCH c.product p
            WHERE p.organization.id = :orgId
              AND c.createdAt >= :from
              AND c.createdAt <= :to
              AND c.actionType = :actionType
            """)
    List<ConsumptionLog> findForOrganizationBetween(
            @Param("orgId") String orgId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("actionType") ConsumptionLog.ActionType actionType
    );

    @Query("""
            SELECT c FROM ConsumptionLog c JOIN FETCH c.product p
            WHERE p.id = :productId AND c.createdAt >= :from AND c.createdAt <= :to
            ORDER BY c.createdAt DESC
            """)
    List<ConsumptionLog> findMovementsForProduct(
            @Param("productId") String productId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT c.source, COALESCE(ABS(SUM(c.quantityChange)), 0)
            FROM ConsumptionLog c JOIN c.product p
            WHERE p.organization.id = :orgId AND c.actionType = :actionType
              AND c.createdAt >= :from AND c.createdAt <= :to
            GROUP BY c.source
            """)
    List<Object[]> sumByChannel(
            @Param("orgId") String orgId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("actionType") ConsumptionLog.ActionType actionType
    );

    @Query("""
        SELECT COALESCE(ABS(SUM(c.quantityChange)) / :days, 0)
        FROM ConsumptionLog c
        WHERE c.product.id = :productId
          AND c.actionType = :actionType
          AND c.createdAt >= :since
        """)
    Double avgDailyConsumption(
        @Param("productId") String productId,
        @Param("since") LocalDateTime since,
        @Param("days") int days,
        @Param("actionType") ConsumptionLog.ActionType actionType
    );
}
