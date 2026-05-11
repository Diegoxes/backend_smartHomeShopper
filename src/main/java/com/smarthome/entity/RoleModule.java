package com.smarthome.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "role_modules")
@Getter
@Setter
@NoArgsConstructor
public class RoleModule {

    @EmbeddedId
    private RoleModuleId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("moduleId")
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
