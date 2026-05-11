package com.smarthome.repository;

import com.smarthome.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.role r LEFT JOIN FETCH r.roleModules rm LEFT JOIN FETCH rm.module WHERE u.id = :id")
    Optional<User> findByIdWithRbac(@Param("id") String id);

    @Modifying
    @Query(value = "UPDATE users SET role_id = :roleId WHERE role_id IS NULL", nativeQuery = true)
    int updateRoleIdWhereNull(@Param("roleId") Long roleId);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByWhatsappNumber(String whatsappNumber);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole_Name(String roleName);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.role ORDER BY LOWER(u.email)")
    java.util.List<User> findAllWithRole();
}
