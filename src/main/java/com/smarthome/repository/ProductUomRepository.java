package com.smarthome.repository;

import com.smarthome.entity.ProductUom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductUomRepository extends JpaRepository<ProductUom, String> {

    List<ProductUom> findByProductIdOrderByMeasureUnit_BaseUnitDesc(String productId);

    @Query("SELECT pu FROM ProductUom pu JOIN FETCH pu.measureUnit WHERE pu.product.id = :productId")
    List<ProductUom> findByProductIdWithUnit(@Param("productId") String productId);

    Optional<ProductUom> findByProductIdAndMeasureUnitId(String productId, String measureUnitId);

    void deleteByProductId(String productId);

    boolean existsByMeasureUnitId(String measureUnitId);
}
