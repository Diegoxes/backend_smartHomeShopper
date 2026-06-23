package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Product;
import com.smarthome.repository.OrganizationSettingsRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.PurchaseRepository;
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
public class ExecutiveDashboardService {

    private final ProductRepository productRepo;
    private final PurchaseRepository purchaseRepo;
    private final OrganizationContextService orgContext;
    private final OrganizationSettingsRepository settingsRepository;
    private final InventoryReportInsightsService reportInsightsService;

    @Transactional(readOnly = true)
    public Dto.ExecutiveDashboardDto executive() {
        String orgId = orgContext.requireActiveOrgId();
        int alertDays = settingsRepository.findByOrganizationId(orgId)
                .map(s -> s.getExpiryAlertDays()).orElse(7);

        List<Product> products = productRepo.findByOrganizationId(orgId);
        BigDecimal totalValue = products.stream()
                .map(p -> {
                    BigDecimal cost = p.getAvgCost() != null ? p.getAvgCost()
                            : (p.getLastCost() != null ? p.getLastCost() : BigDecimal.ZERO);
                    return cost.multiply(BigDecimal.valueOf(p.getQuantity() != null ? p.getQuantity() : 0));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate now = LocalDate.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = now.atTime(LocalTime.MAX);
        BigDecimal monthSpend = purchaseRepo.sumTotalInRange(orgId, null, monthStart, monthEnd);
        if (monthSpend == null) monthSpend = BigDecimal.ZERO;

        long low = products.stream().filter(Product::isLowStock).count();
        long expiring = products.stream().filter(p -> p.isExpiringSoon(alertDays)).count();

        Dto.InventoryReportDto inv = reportInsightsService.inventoryOverview(orgContext.requireUserId());

        return Dto.ExecutiveDashboardDto.builder()
                .totalStockValue(totalValue.setScale(2, RoundingMode.HALF_UP))
                .monthPurchaseSpend(monthSpend.setScale(2, RoundingMode.HALF_UP))
                .lowStockCount(low)
                .expiringCount(expiring)
                .topRotation(inv.getTopConsumed30d())
                .stagnantProductIds(inv.getStagnantProductIds())
                .build();
    }
}
