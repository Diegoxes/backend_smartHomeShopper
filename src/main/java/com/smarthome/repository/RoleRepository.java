package com.smarthome.repository;

import com.smarthome.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);

    @Query("SELECT DISTINCT r FROM Role r LEFT JOIN FETCH r.roleModules rm LEFT JOIN FETCH rm.module WHERE r.name = :name")
    Optional<Role> findByNameWithRoleModules(@Param("name") String name);

    @Query("SELECT DISTINCT r FROM Role r LEFT JOIN FETCH r.roleModules rm LEFT JOIN FETCH rm.module")
    List<Role> findAllWithRoleModules();
}
