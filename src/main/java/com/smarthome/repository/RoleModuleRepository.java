package com.smarthome.repository;

import com.smarthome.entity.RoleModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleModuleRepository extends JpaRepository<RoleModule, Long> {

    Optional<RoleModule> findByRole_IdAndModule_Id(Long roleId, Long moduleId);
}
