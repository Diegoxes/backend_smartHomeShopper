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

    @Query("SELECT p FROM Product p WHERE p.organization.id = :orgId ORDER BY p.name ASC")
    List<Product> findByOrganizationIdOrderByName(@Param("orgId") String orgId);

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

    @Query(value = """
            SELECT p.* FROM products p
            WHERE p.organization_id = :orgId
              AND (:category IS NULL OR p.category = :category)
              AND (
                :q IS NULL
                OR lower(CAST(p.name AS text)) LIKE lower(concat('%', CAST(:q AS text), '%'))
                OR lower(CAST(COALESCE(CAST(p.sku AS text), '') AS text)) LIKE lower(concat('%', CAST(:q AS text), '%'))
                OR lower(CAST(COALESCE(CAST(p.barcode AS text), '') AS text)) LIKE lower(concat('%', CAST(:q AS text), '%'))
              )
            ORDER BY p.name ASC
            """, nativeQuery = true)
    List<Product> findFiltered(
            @Param("orgId") String orgId,
            @Param("category") String category,
            @Param("q") String q);
}
