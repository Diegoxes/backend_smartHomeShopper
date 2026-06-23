package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.MeasureUnit;
import com.smarthome.entity.Product;
import com.smarthome.entity.ProductUom;
import com.smarthome.repository.MeasureUnitRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.ProductUomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductUomService {

    private final ProductUomRepository productUomRepository;
    private final ProductRepository productRepository;
    private final MeasureUnitRepository measureUnitRepository;
    private final MeasureUnitService measureUnitService;
    private final OrganizationContextService orgContext;

    @Transactional(readOnly = true)
    public List<Dto.ProductUomDto> listForProduct(String productId) {
        ownedProduct(productId);
        return productUomRepository.findByProductIdWithUnit(productId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<Dto.ProductUomDto> replaceForProduct(String productId, List<Dto.ProductUomInput> inputs) {
        Product product = ownedProduct(productId);
        String orgId = orgContext.requireActiveOrgId();
        measureUnitService.seedDefaultsIfEmpty(orgId);

        productUomRepository.deleteByProductId(productId);

        List<ProductUom> saved = new ArrayList<>();
        if (inputs != null) {
            for (Dto.ProductUomInput in : inputs) {
                if (in.getFactorToBase() == null || in.getFactorToBase() <= 1) continue;
                MeasureUnit mu = measureUnitService.ownedUnit(in.getMeasureUnitId(), orgId);
                if (mu.isBaseUnit()) continue;
                saved.add(productUomRepository.save(ProductUom.builder()
                        .product(product)
                        .measureUnit(mu)
                        .factorToBase(in.getFactorToBase())
                        .build()));
            }
        }
        syncLegacyField(product, saved);
        return saved.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public void syncBoxUomFromLegacy(Product product) {
        String orgId = product.getOrganization().getId();
        measureUnitService.seedDefaultsIfEmpty(orgId);
        Double factor = product.getUnitsPerPurchaseUnit();
        if (factor == null || factor <= 1) return;

        MeasureUnit box = measureUnitRepository.findByOrganizationIdAndCode(orgId, "BOX")
                .orElse(null);
        if (box == null) return;

        productUomRepository.findByProductIdAndMeasureUnitId(product.getId(), box.getId())
                .ifPresentOrElse(pu -> {
                    pu.setFactorToBase(factor);
                    productUomRepository.save(pu);
                }, () -> productUomRepository.save(ProductUom.builder()
                        .product(product)
                        .measureUnit(box)
                        .factorToBase(factor)
                        .build()));
    }

    @Transactional(readOnly = true)
    public double toBaseUnits(String productId, String measureUnitId, double inputQuantity) {
        if (measureUnitId == null || measureUnitId.isBlank()) {
            return legacyFactorFallback(productId, inputQuantity);
        }
        String orgId = orgContext.requireActiveOrgId();
        MeasureUnit mu = measureUnitService.ownedUnit(measureUnitId, orgId);
        if (mu.isBaseUnit()) return inputQuantity;

        return productUomRepository.findByProductIdAndMeasureUnitId(productId, measureUnitId)
                .map(pu -> inputQuantity * pu.getFactorToBase())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Este producto no tiene configurada la unidad " + mu.getName()));
    }

    private double legacyFactorFallback(String productId, double inputQuantity) {
        Product p = ownedProduct(productId);
        double factor = p.getUnitsPerPurchaseUnit() != null && p.getUnitsPerPurchaseUnit() > 0
                ? p.getUnitsPerPurchaseUnit() : 1.0;
        return inputQuantity * factor;
    }

    @Transactional(readOnly = true)
    public List<Dto.StockBreakdownDto> stockBreakdown(Product product) {
        String orgId = product.getOrganization() != null
                ? product.getOrganization().getId() : orgContext.requireActiveOrgId();
        measureUnitService.seedDefaultsIfEmpty(orgId);

        double qty = product.getQuantity() != null ? product.getQuantity() : 0;
        List<ProductUom> uoms = productUomRepository.findByProductIdWithUnit(product.getId());
        uoms.sort(Comparator.comparing((ProductUom pu) -> pu.getFactorToBase()).reversed());

        List<Dto.StockBreakdownDto> rows = new ArrayList<>();
        for (ProductUom pu : uoms) {
            if (pu.getFactorToBase() <= 1) continue;
            int full = (int) Math.floor(qty / pu.getFactorToBase());
            double remainder = qty - (full * pu.getFactorToBase());
            rows.add(Dto.StockBreakdownDto.builder()
                    .measureUnitId(pu.getMeasureUnit().getId())
                    .name(pu.getMeasureUnit().getName())
                    .code(pu.getMeasureUnit().getCode())
                    .factor(pu.getFactorToBase())
                    .fullUnits(full)
                    .remainder(remainder)
                    .build());
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public String stockDisplay(Product product) {
        double qty = product.getQuantity() != null ? product.getQuantity() : 0;
        List<Dto.StockBreakdownDto> breakdown = stockBreakdown(product);
        if (breakdown.isEmpty()) {
            return String.format("%.0f unidades", qty);
        }
        Dto.StockBreakdownDto primary = breakdown.get(0);
        if (primary.getRemainder() == 0) {
            return String.format("%d %s(s)", primary.getFullUnits(), primary.getName().toLowerCase());
        }
        return String.format("%d %s(s) + %.0f unidades",
                primary.getFullUnits(), primary.getName().toLowerCase(), primary.getRemainder());
    }

    private void syncLegacyField(Product product, List<ProductUom> uoms) {
        Double boxFactor = uoms.stream()
                .filter(pu -> "BOX".equals(pu.getMeasureUnit().getCode()))
                .map(ProductUom::getFactorToBase)
                .findFirst()
                .orElse(uoms.isEmpty() ? null : uoms.get(0).getFactorToBase());
        product.setUnitsPerPurchaseUnit(boxFactor != null ? boxFactor : 1.0);
        productRepository.save(product);
    }

    private Product ownedProduct(String productId) {
        String orgId = orgContext.requireActiveOrgId();
        return productRepository.findByIdAndOrganizationId(productId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
    }

    private Dto.ProductUomDto toDto(ProductUom pu) {
        MeasureUnit mu = pu.getMeasureUnit();
        return Dto.ProductUomDto.builder()
                .id(pu.getId())
                .measureUnitId(mu.getId())
                .code(mu.getCode())
                .name(mu.getName())
                .factorToBase(pu.getFactorToBase())
                .build();
    }
}
