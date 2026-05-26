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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum Status { PENDING, ACTIVE, REJECTED }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    @OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private OrganizationSettings settings;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
