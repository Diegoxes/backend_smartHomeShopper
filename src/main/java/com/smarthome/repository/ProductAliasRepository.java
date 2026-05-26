package com.smarthome.repository;

import com.smarthome.entity.ProductAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductAliasRepository extends JpaRepository<ProductAlias, String> {

    @Query("SELECT a FROM ProductAlias a JOIN FETCH a.product p WHERE p.organization.id = :orgId")
    List<ProductAlias> findAllForOrganization(@Param("orgId") String orgId);

    boolean existsByProductIdAndNormalizedAlias(String productId, String normalizedAlias);
}
