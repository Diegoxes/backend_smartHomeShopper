package com.smarthome.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Estado mínimo para desambiguar menciones tipo "clavitos" → ¿Clavos? en webhooks Twilio sin sesión persistente aparte de BD.
 */
@Entity
@Table(name = "whatsapp_pending_clarifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppPendingClarification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * JSON con acción pendiente ({@code action}, ítem nominal, qty, lista de IDs candidatos ordenados por score).
     */
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
