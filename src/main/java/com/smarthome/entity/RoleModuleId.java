package com.smarthome.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RoleModuleId implements Serializable {

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;
}
