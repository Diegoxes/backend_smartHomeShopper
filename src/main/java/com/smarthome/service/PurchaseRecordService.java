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
    private final OrganizationContextService orgContext;

    @Transactional
    public Dto.PurchaseRowDto createManual(String userId, Dto.CreatePurchaseRequest req) {
        String orgId = orgContext.requireOrgId();
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
    public void attachToRestockIfPriced(Product product, String orgId, Dto.ConsumeRequest req, String currency) {
        if (req.getUnitPrice() == null) return;
        Supplier supplier = ownedSupplierNullable(orgId, req.getSupplierId());
        Purchase pu = Purchase.builder()
                .product(product)
                .supplier(supplier)
                .quantity(req.getAmount())
                .unitPrice(req.getUnitPrice())
                .currency(defaultCurrency(currency))
                .purchasedAt(LocalDateTime.now())
                .source(Purchase.Source.WEB)
                .note(req.getNote())
                .build();
        reconcileTotal(pu);
        purchaseRepo.save(pu);
    }

    @Transactional(readOnly = true)
    public Dto.PurchasesPageDto listFiltered(String userId, String productId, LocalDate from, LocalDate to) {
        String orgId = orgContext.requireOrgId();
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
