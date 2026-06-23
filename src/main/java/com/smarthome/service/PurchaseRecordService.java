package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.*;
import com.smarthome.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseRecordService {

    private final PurchaseRepository purchaseRepo;
    private final ProductRepository productRepo;
    private final SupplierRepository supplierRepo;
    private final MeasureUnitRepository measureUnitRepository;
    private final OrganizationContextService orgContext;

    @Transactional
    public Dto.PurchaseRowDto createManual(String userId, Dto.CreatePurchaseRequest req) {
        String orgId = orgContext.requireActiveOrgId();
        Product p = ownedProduct(orgId, req.getProductId());
        Supplier s = ownedSupplierNullable(orgId, req.getSupplierId());
        LocalDateTime at = req.getPurchasedAt() != null ? req.getPurchasedAt() : LocalDateTime.now();
        Purchase pu = Purchase.builder()
                .product(p)
                .supplier(s)
                .quantity(req.getQuantity())
                .unitPrice(req.getUnitPrice())
                .currency(defaultCurrency(req.getCurrency()))
                .purchasedAt(at)
                .source(Purchase.Source.API)
                .note(req.getNote())
                .build();
        reconcileTotal(pu);
        return toDto(purchaseRepo.save(pu));
    }

    @Transactional
    public void attachToRestockIfPriced(Product product, String orgId, Dto.ConsumeRequest req,
                                       double stockUnits, String currency) {
        if (req.getUnitPrice() == null) return;
        MeasureUnit mu = resolveMeasureUnit(orgId, req.getMeasureUnitId());
        saveWebPurchase(product, orgId, stockUnits, req.getUnitPrice(), req.getSupplierId(),
                defaultCurrency(currency), req.getNote(), mu, req.getAmount(), req.getCostInputMode());
    }

    /** Stock inicial al crear producto: registra compra si hay costo unitario. */
    @Transactional
    public void attachInitialStockIfPriced(Product product, String orgId, double quantity,
                                         java.math.BigDecimal unitCost, String supplierId, String currency) {
        if (unitCost == null || quantity <= 0) return;
        saveWebPurchase(product, orgId, quantity, unitCost, supplierId, defaultCurrency(currency),
                "Stock inicial", null, quantity, "PER_BASE");
    }

    private void saveWebPurchase(Product product, String orgId, double quantity,
                                 java.math.BigDecimal unitPrice, String supplierId,
                                 String currency, String note, MeasureUnit measureUnit,
                                 Double inputQuantity, String costInputMode) {
        Supplier supplier = ownedSupplierNullable(orgId, supplierId);
        Purchase pu = Purchase.builder()
                .product(product)
                .supplier(supplier)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .currency(currency)
                .purchasedAt(LocalDateTime.now())
                .source(Purchase.Source.WEB)
                .note(note)
                .measureUnit(measureUnit)
                .inputQuantity(inputQuantity)
                .costInputMode(costInputMode)
                .build();
        reconcileTotal(pu);
        purchaseRepo.save(pu);
    }

    private MeasureUnit resolveMeasureUnit(String orgId, String measureUnitId) {
        if (measureUnitId == null || measureUnitId.isBlank()) return null;
        return measureUnitRepository.findByIdAndOrganizationId(measureUnitId, orgId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Dto.PurchasesPageDto listFiltered(String userId, String productId, LocalDate from, LocalDate to) {
        String orgId = orgContext.requireActiveOrgId();
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        LocalDateTime fromDt = start.atStartOfDay();
        LocalDateTime toDt = end.atTime(LocalTime.of(23, 59, 59));
        String pidBlank = productId != null && !productId.isBlank() ? productId : null;

        BigDecimal spend = purchaseRepo.sumTotalInRange(orgId, pidBlank, fromDt, toDt);
        if (spend == null) spend = BigDecimal.ZERO;

        List<Dto.PurchaseRowDto> items = purchaseRepo.findFiltered(orgId, pidBlank, fromDt, toDt).stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return Dto.PurchasesPageDto.builder()
                .items(items)
                .periodTotalSpend(spend.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private void reconcileTotal(Purchase pu) {
        if (pu.getUnitPrice() != null && pu.getQuantity() != null) {
            BigDecimal q = BigDecimal.valueOf(pu.getQuantity());
            pu.setTotalAmount(pu.getUnitPrice().multiply(q).setScale(2, RoundingMode.HALF_UP));
        }
    }

    private Supplier ownedSupplierNullable(String orgId, String supplierId) {
        if (supplierId == null || supplierId.isBlank()) return null;
        return supplierRepo.findOwned(supplierId, orgId).orElseThrow(() -> new RuntimeException("Forbidden"));
    }

    private Product ownedProduct(String orgId, String productId) {
        return productRepo.findByIdAndOrganizationId(productId, orgId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    private static String defaultCurrency(String c) {
        return (c == null || c.isBlank()) ? "MXN" : c.trim();
    }

    private Dto.PurchaseRowDto toDto(Purchase pu) {
        return Dto.PurchaseRowDto.builder()
                .id(pu.getId())
                .productId(pu.getProduct().getId())
                .productName(pu.getProduct().getName())
                .supplierId(pu.getSupplier() != null ? pu.getSupplier().getId() : null)
                .supplierName(pu.getSupplier() != null ? pu.getSupplier().getName() : null)
                .quantity(pu.getQuantity())
                .unitPrice(pu.getUnitPrice())
                .totalAmount(pu.getTotalAmount())
                .currency(pu.getCurrency())
                .purchasedAt(pu.getPurchasedAt())
                .source(pu.getSource() != null ? pu.getSource().name() : null)
                .build();
    }
}
