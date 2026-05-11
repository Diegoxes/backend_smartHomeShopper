package com.smarthome.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Permisos por rol y módulo. Clave sustituta {@code id} para evitar fallos de Hibernate 6 con {@code @EmbeddedId}/{@code @MapsId} al persistir.
 */
@Entity
@Table(
        name = "role_modules",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_module_pair", columnNames = {"role_id", "module_id"}))
@Getter
@Setter
@NoArgsConstructor
public class RoleModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private AppModule module;

    @Column(name = "can_create", nullable = false)
    private boolean canCreate;

    @Column(name = "can_read", nullable = false)
    private boolean canRead;

    @Column(name = "can_update", nullable = false)
    private boolean canUpdate;

    @Column(name = "can_delete", nullable = false)
    private boolean canDelete;
}
