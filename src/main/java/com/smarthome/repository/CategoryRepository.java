package com.smarthome.repository;

import com.smarthome.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findByOrganizationIdOrderByNameAsc(String organizationId);
    Optional<Category> findByOrganizationIdAndName(String organizationId, String name);
    boolean existsByOrganizationIdAndName(String organizationId, String name);
}
