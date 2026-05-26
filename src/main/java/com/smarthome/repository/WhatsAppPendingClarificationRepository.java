package com.smarthome.repository;

import com.smarthome.entity.WhatsAppPendingClarification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface WhatsAppPendingClarificationRepository extends JpaRepository<WhatsAppPendingClarification, String> {

    @Query("""
            SELECT p FROM WhatsAppPendingClarification p JOIN FETCH p.user u
            WHERE u.id = :userId AND p.expiresAt > :now
            """)
    Optional<WhatsAppPendingClarification> findActiveForUser(@Param("userId") String userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM WhatsAppPendingClarification w WHERE w.user.id = :uid")
    void purgeForUser(@Param("uid") String userId);
}
