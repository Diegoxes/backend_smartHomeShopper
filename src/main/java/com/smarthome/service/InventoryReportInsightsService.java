package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.ConsumptionLog;
import com.smarthome.entity.Product;
import com.smarthome.entity.Purchase;
import com.smarthome.repository.ConsumptionLogRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.OrganizationSettingsRepository;
import com.smarthome.repository.PurchaseRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryReportInsightsService {

    private final ProductRepository productRepo;
    private final ConsumptionLogRepository consumptionLogRepo;
    private final PurchaseRepository purchaseRepo;
    private final OrganizationContextService orgContext;
    private final OrganizationSettingsRepository settingsRepository;

    @Transactional(readOnly = true)
    public Dto.RotationReportDto rotation(String userId, LocalDate from, LocalDate to) {
        String orgId = orgContext.requireOrgId();
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(horizonDays(orgId));
        LocalDateTime fromDt = start.atStartOfDay();
        LocalDateTime toDt = end.atTime(LocalTime.MAX);
        long periodDays = Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
        int daysParam = Math.max(1, Math.min((int) periodDays, 90));

        List<Product> products = productRepo.findByOrganizationId(orgId);
        Map<String, Double> consumedQty = aggregatedConsumedQty(orgId, fromDt, toDt);

        List<Dto.RotationReportRowDto> rows = products.stream().map(p -> {
            double units = consumedQty.getOrDefault(p.getId(), 0d);
            Double avgDaily = consumptionLogRepo.avgDailyConsumption(
                    p.getId(), fromDt, daysParam, ConsumptionLog.ActionType.CONSUMED);
            Double estDays = estimateDaysRemaining(p, avgDaily);
            return Dto.RotationReportRowDto.builder()
                    .productId(p.getId())
                    .productName(p.getName())
                    .category(Optional.ofNullable(p.getCategory()).orElse("Sin categoría"))
                    .unitsConsumed(round2(units))
                    .avgDailyConsumption(avgDaily != null ? round4(avgDaily) : null)
                    .estimatedDaysRemaining(estDays != null ? round2(estDays) : null)
                    .velocity(velocityLabel(avgDaily))
                    .build();
        }).sorted(Comparator.comparingDouble(Dto.RotationReportRowDto::getUnitsConsumed).reversed())
                .collect(Collectors.toList());

        return Dto.RotationReportDto.builder()
                .fromInclusive(start)
                .toInclusive(end)
                .rows(rows)
                .build();
    }

    @Transactional(readOnly = true)
    public Dto.InventoryReportDto inventoryOverview(String userId) {
        return inventoryOverviewForOrg(orgContext.requireOrgId());
    }

    @Transactional(readOnly = true)
    public Dto.InventoryReportDto inventoryOverviewForOrg(String orgId) {
        List<Product> all = productRepo.findByOrganizationId(orgId);

        LocalDate today = LocalDate.now();
        LocalDateTime last30start = today.minusDays(30).atStartOfDay();
        LocalDateTime last30end = today.atTime(LocalTime.MAX);

        Map<String, Double> topMap = aggregatedConsumedQty(orgId, last30start, last30end);
        Map<String, DtoGrouping> byCat = new TreeMap<>();
        BigDecimal valuation = BigDecimal.ZERO;

        for (Product p : all) {
            String catKey = Optional.ofNullable(p.getCategory()).filter(s -> !s.isBlank()).orElse("Sin categoría");
            DtoGrouping g = byCat.computeIfAbsent(catKey, k -> new DtoGrouping(catKey));

            BigDecimal unitCost = p.getAvgCost() != null ? p.getAvgCost()
                    : purchaseRepo.findFirstByProductIdOrderByPurchasedAtDesc(p.getId())
                    .map(Purchase::getUnitPrice).orElse(null);

            BigDecimal lineVal = BigDecimal.ZERO;
            if (unitCost != null && p.getQuantity() != null) {
                lineVal = unitCost.multiply(BigDecimal.valueOf(p.getQuantity())).setScale(2, RoundingMode.HALF_UP);
                valuation = valuation.add(lineVal);
            }

            g.sku++;
            if (p.getQuantity() != null) g.quantitySum += p.getQuantity();
            g.spendEstimated = g.spendEstimated.add(lineVal);
        }

        List<Dto.CategoryBreakdownDto> catRows = byCat.values().stream()
                .map(g -> Dto.CategoryBreakdownDto.builder()
                        .category(g.getCat())
                        .skuCount(g.sku)
                        .quantitySum(round2(g.quantitySum))
                        .estimatedSpend(g.spendEstimated.setScale(2, RoundingMode.HALF_UP))
                        .build())
                .collect(Collectors.toList());

        LocalDateTime sinceStagnant = today.minusDays(60).atStartOfDay();
        List<String> activeIds = consumptionLogRepo.findDistinctConsumedProductIdsSince(
                orgId, sinceStagnant, ConsumptionLog.ActionType.CONSUMED);
        Set<String> active = new HashSet<>(activeIds);
        List<String> stagnant = all.stream().map(Product::getId).filter(id -> !active.contains(id)).collect(Collectors.toList());

        List<Dto.RotationReportRowDto> top10 = topMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> productRepo.findByIdAndOrganizationId(e.getKey(), orgId))
                .flatMap(Optional::stream)
                .map(p -> Dto.RotationReportRowDto.builder()
                        .productId(p.getId())
                        .productName(p.getName())
                        .category(Optional.ofNullable(p.getCategory()).orElse("Sin categoría"))
                        .unitsConsumed(round2(topMap.getOrDefault(p.getId(), 0d)))
                        .build())
                .collect(Collectors.toList());

        return Dto.InventoryReportDto.builder()
                .totalSku(all.size())
                .totalEstimatedValue(valuation.setScale(2, RoundingMode.HALF_UP))
                .byCategory(catRows)
                .topConsumed30d(top10)
                .stagnantProductIds(stagnant)
                .build();
    }

    @Transactional(readOnly = true)
    public List<Dto.CategoryBreakdownDto> byCategory(String userId) {
        return inventoryOverview(userId).getByCategory();
    }

    @Transactional(readOnly = true)
    public List<Dto.SupplierSpendRowDto> bySupplier(String userId, LocalDate from, LocalDate to) {
        String orgId = orgContext.requireOrgId();
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        return purchaseRepo.sumBySupplier(orgId, start.atStartOfDay(), end.atTime(LocalTime.MAX)).stream()
                .map(row -> Dto.SupplierSpendRowDto.builder()
                        .supplierId(row[0] != null ? row[0].toString() : null)
                        .supplierName(row[1] != null ? row[1].toString() : "Sin proveedor")
                        .totalSpend(((BigDecimal) row[2]).setScale(2, RoundingMode.HALF_UP))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Dto.ChannelReportRowDto> byChannel(String userId, LocalDate from, LocalDate to) {
        String orgId = orgContext.requireOrgId();
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        return consumptionLogRepo.sumByChannel(orgId, start.atStartOfDay(), end.atTime(LocalTime.MAX),
                        ConsumptionLog.ActionType.CONSUMED).stream()
                .map(row -> Dto.ChannelReportRowDto.builder()
                        .channel(row[0] != null ? row[0].toString() : "UNKNOWN")
                        .unitsConsumed(row[1] != null ? ((Number) row[1]).doubleValue() : 0d)
                        .build())
                .collect(Collectors.toList());
    }

    private Map<String, Double> aggregatedConsumedQty(String orgId, LocalDateTime from, LocalDateTime to) {
        List<ConsumptionLog> logs = consumptionLogRepo.findForOrganizationBetween(
                orgId, from, to, ConsumptionLog.ActionType.CONSUMED);
        Map<String, Double> map = new HashMap<>();
        for (ConsumptionLog c : logs) {
            if (c.getProduct() == null) continue;
            double absQty = Math.abs(c.getQuantityChange() != null ? c.getQuantityChange() : 0d);
            map.merge(c.getProduct().getId(), absQty, Double::sum);
        }
        return map;
    }

    private int horizonDays(String orgId) {
        return settingsRepository.findByOrganizationId(orgId)
                .map(s -> s.getPredictionHorizonDays()).orElse(30);
    }

    private Double estimateDaysRemaining(Product p, Double avgDailyConsumption) {
        if (avgDailyConsumption == null || avgDailyConsumption <= 0.000001) return null;
        if (p.getQuantity() == null) return null;
        return p.getQuantity() / avgDailyConsumption;
    }

    private String velocityLabel(Double avgDaily) {
        if (avgDaily == null || avgDaily <= 0.000001) return "UNKNOWN";
        if (avgDaily >= 1.0) return "FAST";
        if (avgDaily >= 0.25) return "NORMAL";
        return "SLOW";
    }

    private static double round2(double v) { return Math.round(v * 100d) / 100d; }
    private static double round4(double v) { return Math.round(v * 10000d) / 10000d; }

    @Getter
    private static final class DtoGrouping {
        private final String cat;
        private long sku;
        private double quantitySum;
        private BigDecimal spendEstimated = BigDecimal.ZERO;
        private DtoGrouping(String cat) { this.cat = cat; }
    }
}
