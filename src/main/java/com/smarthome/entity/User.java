package com.smarthome.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(name = "whatsapp_number")
    private String whatsappNumber;

    /**
     * Nullable en BD para que Hibernate pueda hacer {@code ALTER ADD COLUMN} con filas ya existentes;
     * el arranque asigna rol MEMBER a quien tenga {@code role_id} nulo.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "role_id", nullable = true)
    private Role role;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<OrganizationMember> memberships;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
