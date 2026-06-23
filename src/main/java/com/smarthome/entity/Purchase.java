package com.smarthome.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchases")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(nullable = false)
    private Double quantity;

    @Column(name = "unit_price", precision = 14, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", length = 8)
    @Builder.Default
    private String currency = "MXN";

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    @Column(name = "source")
    @Enumerated(EnumType.STRING)
    private Source source;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measure_unit_id")
    private MeasureUnit measureUnit;

    @Column(name = "input_quantity")
    private Double inputQuantity;

    @Column(name = "cost_input_mode", length = 32)
    private String costInputMode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum Source {
        WEB, WHATSAPP, API
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (purchasedAt == null) {
            purchasedAt = LocalDateTime.now();
        }
        if (unitPrice != null && quantity != null) {
            BigDecimal q = BigDecimal.valueOf(quantity);
            totalAmount = unitPrice.multiply(q).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
