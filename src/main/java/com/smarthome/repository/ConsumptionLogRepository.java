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
        SELECT COALESCE(ABS(SUM(c.quantityChange)) / :days, 0)
        FROM ConsumptionLog c
        WHERE c.product.id = :productId
          AND c.actionType = 'CONSUMED'
          AND c.createdAt >= :since
        """)
    Double avgDailyConsumption(
        @Param("productId") String productId,
        @Param("since") LocalDateTime since,
        @Param("days") int days
    );
}
