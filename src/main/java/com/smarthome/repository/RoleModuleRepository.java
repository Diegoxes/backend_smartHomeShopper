package com.smarthome.repository;

import com.smarthome.entity.RoleModule;
import com.smarthome.entity.RoleModuleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleModuleRepository extends JpaRepository<RoleModule, RoleModuleId> {
}
