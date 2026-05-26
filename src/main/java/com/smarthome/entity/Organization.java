package com.smarthome.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "organizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "industry")
    private String industry;

    @Column(nullable = false)
    @Builder.Default
    private String currency = "MXN";

    @Column(name = "country")
    private String country;

    @Column(name = "timezone")
    @Builder.Default
    private String timezone = "America/Mexico_City";

    @Column(name = "max_members", nullable = false)
    @Builder.Default
    private Integer maxMembers = 20;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private OrganizationSettings settings;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
