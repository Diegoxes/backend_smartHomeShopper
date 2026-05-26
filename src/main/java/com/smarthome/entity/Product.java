package com.smarthome.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /** Legacy / auditoría: quien creó el producto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "sku", length = 64)
    private String sku;

    @Column(name = "internal_code", length = 64)
    private String internalCode;

    @Column(name = "sale_price", precision = 14, scale = 4)
    private java.math.BigDecimal salePrice;

    @Column(name = "last_cost", precision = 14, scale = 4)
    private java.math.BigDecimal lastCost;

    @Column(name = "avg_cost", precision = 14, scale = 4)
    private java.math.BigDecimal avgCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_unit")
    private UnitType purchaseUnit;

    @Column(name = "units_per_purchase_unit")
    private Double unitsPerPurchaseUnit;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double quantity;

    @Column(name = "min_quantity", nullable = false)
    private Double minQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitType unit;

    @Column(name = "consumption_per_use")
    private Double consumptionPerUse;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "category")
    private String category;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ConsumptionLog> consumptionLogs;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isLowStock() {
        return quantity <= minQuantity;
    }

    public boolean isExpiringSoon() {
        return isExpiringSoon(7);
    }

    public boolean isExpiringSoon(int alertDays) {
        if (expiryDate == null) return false;
        return expiryDate.isBefore(LocalDate.now().plusDays(alertDays));
    }

    public enum UnitType {
        UNIT, KG, LITER, GRAM, ML, PACK
    }
}
