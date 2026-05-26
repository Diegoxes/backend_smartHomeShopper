package com.smarthome.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_aliases",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_normalized_alias",
                columnNames = {"product_id", "normalized_alias"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Texto original indicado por el usuario (opcional pero útil en UI). */
    @Column(name = "alias_text", nullable = false, length = 255)
    private String aliasText;

    @Column(name = "normalized_alias", nullable = false, length = 255)
    private String normalizedAlias;

    @Column(name = "learned_whatsapp")
    private boolean learnedWhatsApp;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
