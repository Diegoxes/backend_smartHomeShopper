package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Organization;
import com.smarthome.entity.Product;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.OrganizationSettingsRepository;
import com.smarthome.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusinessContextService {

    private static final int MAX_PRODUCT_LINES = 40;

    private final OrganizationRepository organizationRepository;
    private final OrganizationSettingsRepository settingsRepository;
    private final ProductRepository productRepository;
    private final InventoryReportInsightsService reportInsightsService;

    @Transactional(readOnly = true)
    public String buildContextForOrg(String orgId) {
        Organization org = organizationRepository.findById(orgId).orElse(null);
        Dto.InventoryReportDto overview = reportInsightsService.inventoryOverviewForOrg(orgId);

        int alertDays = settingsRepository.findByOrganizationId(orgId)
                .map(s -> s.getExpiryAlertDays())
                .orElse(7);

        List<Product> lowStock = productRepository.findLowStockByOrganizationId(orgId);
        List<Product> expiring = productRepository.findExpiringByOrganizationId(
                orgId, LocalDate.now().plusDays(alertDays));
        List<Product> catalog = productRepository.findByOrganizationIdOrderByName(orgId);

        StringBuilder sb = new StringBuilder();
        sb.append("Organización: ").append(org != null ? org.getName() : orgId);
        if (org != null && org.getIndustry() != null) {
            sb.append(" (industria: ").append(org.getIndustry()).append(")");
        }
        sb.append("\n");
        sb.append("Total SKU: ").append(overview.getTotalSku());
        sb.append(" | Valor estimado: ").append(overview.getTotalEstimatedValue()).append("\n");
        sb.append("Stock bajo: ").append(lowStock.size())
                .append(" | Por vencer (").append(alertDays).append(" días): ")
                .append(expiring.size()).append("\n");

        if (!lowStock.isEmpty()) {
            sb.append("\nProductos con stock bajo:\n");
            lowStock.stream().limit(15).forEach(p ->
                    sb.append("- ").append(p.getName())
                            .append(": ").append(p.getQuantity())
                            .append(" / mín ").append(p.getMinQuantity()).append("\n"));
        }

        if (!expiring.isEmpty()) {
            sb.append("\nProductos por vencer:\n");
            expiring.stream().limit(15).forEach(p ->
                    sb.append("- ").append(p.getName())
                            .append(" vence ").append(p.getExpiryDate()).append("\n"));
        }

        sb.append("\nCatálogo (nombre | cantidad | SKU):\n");
        sb.append(catalog.stream()
                .limit(MAX_PRODUCT_LINES)
                .map(p -> p.getName() + " | " + p.getQuantity() + " " + p.getUnit().name()
                        + " | " + p.getSku())
                .collect(Collectors.joining("\n")));

        if (catalog.size() > MAX_PRODUCT_LINES) {
            sb.append("\n... y ").append(catalog.size() - MAX_PRODUCT_LINES).append(" productos más.");
        }

        return sb.toString();
    }
}
