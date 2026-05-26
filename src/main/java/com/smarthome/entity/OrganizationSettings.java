package com.smarthome.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "organization_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, unique = true)
    private Organization organization;

    @Column(name = "expiry_alert_days", nullable = false)
    @Builder.Default
    private Integer expiryAlertDays = 7;

    @Column(name = "prediction_horizon_days", nullable = false)
    @Builder.Default
    private Integer predictionHorizonDays = 30;
}
