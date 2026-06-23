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
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepo;
    private final ConsumptionLogRepository logRepo;
    private final PurchaseRepository purchaseRepo;
    private final UserRepository userRepo;
    private final OrganizationRepository organizationRepository;
    private final OrganizationSettingsRepository settingsRepository;
    private final OrganizationContextService orgContext;
    private final PurchaseRecordService purchaseRecordService;
    private final StockLevelRepository stockLevelRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryLotService inventoryLotService;
    private final ProductUomService productUomService;
    private final MeasureUnitService measureUnitService;

    public List<Dto.ProductResponse> listFiltered(Boolean lowStock, Boolean expiringSoon,
                                                  Integer stagnantDays, String category, String q) {
        String orgId = orgContext.requireActiveOrgId();
        int alertDays = alertDaysForOrg(orgId);
        String cat = blankToNull(category);
        String query = blankToNull(q);
        List<Product> products = query == null && cat == null
                ? productRepo.findByOrganizationIdOrderByName(orgId)
                : productRepo.findFiltered(orgId, cat, query);

        LocalDateTime stagnantCutoff = stagnantDays != null && stagnantDays > 0
                ? LocalDateTime.now().minusDays(stagnantDays) : null;

        return products.stream()
                .filter(p -> lowStock == null || !lowStock || p.isLowStock())
                .filter(p -> expiringSoon == null || !expiringSoon || p.isExpiringSoon(alertDays))
                .filter(p -> stagnantCutoff == null || isStagnant(p, stagnantCutoff))
                .map(p -> toResponse(p, alertDays))
                .collect(Collectors.toList());
    }

    public List<Dto.ProductResponse> getAllByUser(String userId) {
        return listFiltered(null, null, null, null, null);
    }

    public Dto.ProductResponse getById(String productId, String userId) {
        Product p = findOwned(productId);
        return toResponse(p, alertDaysForOrg(orgContext.requireActiveOrgId()));
    }

    @Transactional //create
    public Dto.ProductResponse create(String userId, Dto.CreateProductRequest req) {
        String orgId = orgContext.requireActiveOrgId();
        if (productRepo.findByOrganizationIdAndSku(orgId, req.getSku().trim()).isPresent()) {
            throw new RuntimeException("SKU ya existe en esta organización");
        }
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Product p = Product.builder()
                .organization(org)
                .user(user)
                .sku(req.getSku().trim())
                .internalCode(req.getInternalCode())
                .name(req.getName())
                .quantity(req.getQuantity())
                .minQuantity(req.getMinQuantity())
                .unit(req.getUnit())
                .consumptionPerUse(req.getConsumptionPerUse() != null ? req.getConsumptionPerUse() : 1.0)
                .expiryDate(req.getExpiryDate())
                .barcode(req.getBarcode())
                .category(req.getCategory())
                .imageUrl(req.getImageUrl())
                .salePrice(req.getSalePrice())
                .purchaseUnit(req.getPurchaseUnit() != null ? req.getPurchaseUnit() : req.getUnit())
                .unitsPerPurchaseUnit(req.getUnitsPerPurchaseUnit() != null ? req.getUnitsPerPurchaseUnit() : 1.0)
                .build();
        applyUnitCost(p, req.getUnitCost());
        p = productRepo.save(p);
        syncDefaultStockLevel(orgId, p);
        productUomService.syncBoxUomFromLegacy(p);
        if (req.getProductUoms() != null && !req.getProductUoms().isEmpty()) {
            productUomService.replaceForProduct(p.getId(), req.getProductUoms());
            p = productRepo.findById(p.getId()).orElse(p);
        }
        purchaseRecordService.attachInitialStockIfPriced(
                p, orgId, req.getQuantity(), req.getUnitCost(), req.getSupplierId(), "MXN");
        return toResponse(p, alertDaysForOrg(orgId));
    }

    @Transactional
    public Dto.ProductResponse update(String productId, String userId, Dto.UpdateProductRequest req) {
        String orgId = orgContext.requireActiveOrgId();
        Product p = findOwned(productId);
        final String currentProductId = p.getId();
        if (req.getSku() != null && !req.getSku().equals(p.getSku())) {
            productRepo.findByOrganizationIdAndSku(orgId, req.getSku().trim()).ifPresent(existing -> {
                if (!existing.getId().equals(currentProductId)) throw new RuntimeException("SKU ya existe");
            });
            p.setSku(req.getSku().trim());
        }
        if (req.getInternalCode() != null) p.setInternalCode(req.getInternalCode());
        if (req.getName() != null) p.setName(req.getName());
        if (req.getQuantity() != null) p.setQuantity(req.getQuantity());
        if (req.getMinQuantity() != null) p.setMinQuantity(req.getMinQuantity());
        if (req.getUnit() != null) p.setUnit(req.getUnit());
        if (req.getConsumptionPerUse() != null) p.setConsumptionPerUse(req.getConsumptionPerUse());
        if (req.getExpiryDate() != null) p.setExpiryDate(req.getExpiryDate());
        if (req.getCategory() != null) p.setCategory(req.getCategory());
        if (req.getSalePrice() != null) p.setSalePrice(req.getSalePrice());
        if (req.getUnitCost() != null) applyUnitCost(p, req.getUnitCost());
        if (req.getPurchaseUnit() != null) p.setPurchaseUnit(req.getPurchaseUnit());
        if (req.getUnitsPerPurchaseUnit() != null) {
            p.setUnitsPerPurchaseUnit(req.getUnitsPerPurchaseUnit());
            productUomService.syncBoxUomFromLegacy(p);
        }
        return toResponse(productRepo.save(p), alertDaysForOrg(orgId));
    }

    @Transactional
    public Dto.ProductResponse consume(String productId, String userId, Dto.ConsumeRequest req) {
        Product p = findOwned(productId);
        double stockUnits = resolveBaseUnits(p, req, false);
        MeasureUnit mu = resolveMeasureUnit(req.getMeasureUnitId());
        inventoryLotService.consumeFifo(p, stockUnits);
        double newQty = Math.max(0, p.getQuantity() - stockUnits);
        p.setQuantity(newQty);
        productRepo.save(p);
        syncDefaultStockLevel(orgContext.requireActiveOrgId(), p);

        logRepo.save(ConsumptionLog.builder()
                .product(p)
                .quantityChange(-stockUnits)
                .actionType(ConsumptionLog.ActionType.CONSUMED)
                .source(ConsumptionLog.Source.WEB)
                .note(req.getNote())
                .measureUnit(mu)
                .inputQuantity(req.getAmount())
                .build());
        return toResponse(p, alertDaysForOrg(orgContext.requireActiveOrgId()));
    }

    @Transactional
    public Dto.ProductResponse restock(String productId, String userId, Dto.ConsumeRequest req) {
        String orgId = orgContext.requireActiveOrgId();
        Product p = findOwned(productId);
        double stockUnits = resolveBaseUnits(p, req, true);
        MeasureUnit mu = resolveMeasureUnit(req.getMeasureUnitId());
        p.setQuantity(p.getQuantity() + stockUnits);
        updateCostsOnRestock(p, req.getUnitPrice(), stockUnits);
        productRepo.save(p);
        syncDefaultStockLevel(orgId, p);
        inventoryLotService.addLot(p, stockUnits, p.getExpiryDate());

        logRepo.save(ConsumptionLog.builder()
                .product(p)
                .quantityChange(stockUnits)
                .actionType(ConsumptionLog.ActionType.RESTOCKED)
                .source(ConsumptionLog.Source.WEB)
                .note(req.getNote())
                .measureUnit(mu)
                .inputQuantity(req.getAmount())
                .build());

        purchaseRecordService.attachToRestockIfPriced(p, orgId, req, stockUnits, "MXN");
        return toResponse(p, alertDaysForOrg(orgId));
    }

    @Transactional
    public Dto.ProductResponse adjust(String productId, Dto.AdjustStockRequest req) {
        String orgId = orgContext.requireActiveOrgId();
        Product p = findOwned(productId);
        p.setQuantity(Math.max(0, p.getQuantity() + req.getDelta()));
        productRepo.save(p);
        syncDefaultStockLevel(orgId, p);

        logRepo.save(ConsumptionLog.builder()
                .product(p)
                .quantityChange(req.getDelta())
                .actionType(ConsumptionLog.ActionType.ADJUSTED)
                .source(ConsumptionLog.Source.WEB)
                .note(req.getReason())
                .build());
        return toResponse(p, alertDaysForOrg(orgId));
    }

    @Transactional(readOnly = true)
    public List<Dto.ProductMovementDto> movements(String productId, LocalDate from, LocalDate to) {
        findOwned(productId);
        LocalDateTime fromDt = (from != null ? from : LocalDate.now().minusDays(30)).atStartOfDay();
        LocalDateTime toDt = (to != null ? to : LocalDate.now()).atTime(23, 59, 59);

        List<Dto.ProductMovementDto> logs = logRepo.findMovementsForProduct(productId, fromDt, toDt).stream()
                .map(c -> Dto.ProductMovementDto.builder()
                        .at(c.getCreatedAt())
                        .actionType(c.getActionType().name())
                        .quantityChange(c.getQuantityChange())
                        .source(c.getSource() != null ? c.getSource().name() : null)
                        .note(c.getNote())
                        .build())
                .collect(Collectors.toList());

        purchaseRepo.findFiltered(orgContext.requireActiveOrgId(), productId, fromDt, toDt).stream()
                .map(pu -> Dto.ProductMovementDto.builder()
                        .at(pu.getPurchasedAt())
                        .actionType("PURCHASE")
                        .quantityChange(pu.getQuantity())
                        .source(pu.getSource() != null ? pu.getSource().name() : "PURCHASE")
                        .note(pu.getNote())
                        .purchaseId(pu.getId())
                        .build())
                .forEach(logs::add);

        logs.sort(Comparator.comparing(Dto.ProductMovementDto::getAt).reversed());
        return logs;
    }

    @Transactional
    public void delete(String productId, String userId) {
        Product p = findOwned(productId);
        productRepo.delete(p);
    }

    public Dto.DashboardResponse getDashboard(String userId) {
        String orgId = orgContext.requireActiveOrgId();
        int alertDays = alertDaysForOrg(orgId);
        List<Dto.ProductResponse> all = productRepo.findByOrganizationId(orgId).stream()
                .map(p -> toResponse(p, alertDays)).collect(Collectors.toList());
        List<Dto.ProductResponse> lowStock = all.stream().filter(Dto.ProductResponse::isLowStock).collect(Collectors.toList());
        List<Dto.ProductResponse> expiring = all.stream().filter(Dto.ProductResponse::isExpiringSoon).collect(Collectors.toList());

        return Dto.DashboardResponse.builder()
                .totalProducts(all.size())
                .lowStockCount(lowStock.size())
                .expiringCount(expiring.size())
                .lowStockProducts(lowStock)
                .expiringProducts(expiring)
                .allProducts(all)
                .build();
    }

    private Product findOwned(String productId) {
        String orgId = orgContext.requireActiveOrgId();
        return productRepo.findByIdAndOrganizationId(productId, orgId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    private int alertDaysForOrg(String orgId) {
        return settingsRepository.findByOrganizationId(orgId)
                .map(OrganizationSettings::getExpiryAlertDays)
                .orElse(7);
    }

    private int horizonDaysForOrg(String orgId) {
        return settingsRepository.findByOrganizationId(orgId)
                .map(OrganizationSettings::getPredictionHorizonDays)
                .orElse(30);
    }

    private boolean isStagnant(Product p, LocalDateTime cutoff) {
        return logRepo.findMovementsForProduct(p.getId(), cutoff, LocalDateTime.now()).stream()
                .noneMatch(c -> c.getActionType() == ConsumptionLog.ActionType.CONSUMED);
    }

    private void syncDefaultStockLevel(String orgId, Product p) {
        warehouseRepository.findDefaultByOrganizationId(orgId).ifPresent(wh ->
                stockLevelRepository.findByProductIdAndWarehouseId(p.getId(), wh.getId())
                        .ifPresentOrElse(sl -> {
                            sl.setQuantity(p.getQuantity());
                            stockLevelRepository.save(sl);
                        }, () -> stockLevelRepository.save(StockLevel.builder()
                                .product(p)
                                .warehouse(wh)
                                .quantity(p.getQuantity())
                                .build())));
    }

    private double resolveBaseUnits(Product p, Dto.ConsumeRequest req, boolean restockLegacyMultiply) {
        if (req.getMeasureUnitId() != null && !req.getMeasureUnitId().isBlank()) {
            return productUomService.toBaseUnits(p.getId(), req.getMeasureUnitId(), req.getAmount());
        }
        if (restockLegacyMultiply) {
            return toStockUnits(p, req.getAmount());
        }
        return req.getAmount();
    }

    private MeasureUnit resolveMeasureUnit(String measureUnitId) {
        if (measureUnitId == null || measureUnitId.isBlank()) return null;
        return measureUnitService.ownedUnit(measureUnitId, orgContext.requireActiveOrgId());
    }

    private double toStockUnits(Product p, double purchaseQty) {
        double factor = p.getUnitsPerPurchaseUnit() != null && p.getUnitsPerPurchaseUnit() > 0
                ? p.getUnitsPerPurchaseUnit() : 1.0;
        return purchaseQty * factor;
    }

    private void applyUnitCost(Product p, BigDecimal unitCost) {
        if (unitCost == null) return;
        p.setLastCost(unitCost);
        p.setAvgCost(unitCost);
    }

    private void updateCostsOnRestock(Product p, BigDecimal unitPrice, double stockUnits) {
        if (unitPrice == null) return;
        p.setLastCost(unitPrice);
        double prevQty = Math.max(0, p.getQuantity() - stockUnits);
        BigDecimal prevAvg = p.getAvgCost() != null ? p.getAvgCost() : unitPrice;
        if (prevQty + stockUnits <= 0) {
            p.setAvgCost(unitPrice);
            return;
        }
        BigDecimal totalVal = prevAvg.multiply(BigDecimal.valueOf(prevQty))
                .add(unitPrice.multiply(BigDecimal.valueOf(stockUnits)));
        p.setAvgCost(totalVal.divide(BigDecimal.valueOf(prevQty + stockUnits), 4, RoundingMode.HALF_UP));
    }

    private Double predictDaysUntilEmpty(Product p, int horizonDays) {
        LocalDateTime since = LocalDateTime.now().minusDays(horizonDays);
        Double avg = logRepo.avgDailyConsumption(p.getId(), since, horizonDays, ConsumptionLog.ActionType.CONSUMED);
        if (avg == null || avg == 0) return null;
        return p.getQuantity() / avg;
    }

    public Dto.ProductResponse toResponse(Product p) {
        String orgId = p.getOrganization() != null ? p.getOrganization().getId() : orgContext.requireActiveOrgId();
        return toResponse(p, alertDaysForOrg(orgId));
    }

    public Dto.ProductResponse toResponse(Product p, int alertDays) {
        String orgId = p.getOrganization() != null ? p.getOrganization().getId() : orgContext.requireActiveOrgId();
        int horizon = horizonDaysForOrg(orgId);
        BigDecimal margin = null;
        if (p.getSalePrice() != null && p.getAvgCost() != null && p.getAvgCost().compareTo(BigDecimal.ZERO) > 0) {
            margin = p.getSalePrice().subtract(p.getAvgCost())
                    .divide(p.getAvgCost(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return Dto.ProductResponse.builder()
                .id(p.getId())
                .sku(p.getSku())
                .internalCode(p.getInternalCode())
                .name(p.getName())
                .quantity(p.getQuantity())
                .minQuantity(p.getMinQuantity())
                .unit(p.getUnit().name())
                .consumptionPerUse(p.getConsumptionPerUse())
                .expiryDate(p.getExpiryDate())
                .barcode(p.getBarcode())
                .category(p.getCategory())
                .imageUrl(p.getImageUrl())
                .salePrice(p.getSalePrice())
                .lastCost(p.getLastCost())
                .avgCost(p.getAvgCost())
                .marginPercent(margin)
                .purchaseUnit(p.getPurchaseUnit() != null ? p.getPurchaseUnit().name() : null)
                .unitsPerPurchaseUnit(p.getUnitsPerPurchaseUnit())
                .productUoms(productUomService.listForProduct(p.getId()))
                .stockBreakdown(productUomService.stockBreakdown(p))
                .stockDisplay(productUomService.stockDisplay(p))
                .lowStock(p.isLowStock())
                .expiringSoon(p.isExpiringSoon(alertDays))
                .daysUntilEmpty(predictDaysUntilEmpty(p, horizon))
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
