package com.smarthome.repository;

import com.smarthome.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    List<Product> findByUserId(String userId);

    List<Product> findByUserIdAndCategory(String userId, String category);

    @Query("SELECT p FROM Product p WHERE p.user.id = :userId AND p.quantity <= p.minQuantity")
    List<Product> findLowStockByUserId(@Param("userId") String userId);

    @Query("SELECT p FROM Product p WHERE p.user.id = :userId AND p.expiryDate IS NOT NULL AND p.expiryDate <= :deadline")
    List<Product> findExpiringByUserId(@Param("userId") String userId, @Param("deadline") LocalDate deadline);

    Optional<Product> findByUserIdAndBarcode(String userId, String barcode);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.user.id = :userId")
    long countByUserId(@Param("userId") String userId);
}
