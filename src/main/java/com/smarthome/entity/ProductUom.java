package com.smarthome.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_uoms", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "measure_unit_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductUom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measure_unit_id", nullable = false)
    private MeasureUnit measureUnit;

    @Column(name = "factor_to_base", nullable = false)
    private Double factorToBase;
}
