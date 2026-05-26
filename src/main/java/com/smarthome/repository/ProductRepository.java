package com.smarthome.repository;

import com.smarthome.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    List<Product> findByOrganizationId(String organizationId);

    @Query("SELECT p FROM Product p WHERE p.organization.id = :orgId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Product> searchByOrganizationId(@Param("orgId") String orgId, @Param("q") String q);

    @Query("SELECT p FROM Product p WHERE p.organization.id = :orgId AND p.category = :category")
    List<Product> findByOrganizationIdAndCategory(@Param("orgId") String orgId, @Param("category") String category);

    @Query("SELECT p FROM Product p WHERE p.organization.id = :orgId AND p.quantity <= p.minQuantity")
    List<Product> findLowStockByOrganizationId(@Param("orgId") String orgId);

    @Query("SELECT p FROM Product p WHERE p.organization.id = :orgId AND p.expiryDate IS NOT NULL AND p.expiryDate <= :deadline")
    List<Product> findExpiringByOrganizationId(@Param("orgId") String orgId, @Param("deadline") LocalDate deadline);

    Optional<Product> findByOrganizationIdAndBarcode(String organizationId, String barcode);

    Optional<Product> findByOrganizationIdAndSku(String organizationId, String sku);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.organization.id = :orgId")
    long countByOrganizationId(@Param("orgId") String orgId);

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.organization.id = :orgId")
    Optional<Product> findByIdAndOrganizationId(@Param("id") String id, @Param("orgId") String orgId);

    @Query("""
            SELECT p FROM Product p WHERE p.organization.id = :orgId
            AND (:category IS NULL OR p.category = :category)
            AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(COALESCE(p.sku, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY p.name ASC
            """)
    List<Product> findFiltered(
            @Param("orgId") String orgId,
            @Param("category") String category,
            @Param("q") String q);
}
