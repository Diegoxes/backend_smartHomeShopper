package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.OrganizationSettingsRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessContextServiceTest {

    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationSettingsRepository settingsRepository;
    @Mock ProductRepository productRepository;
    @Mock InventoryReportInsightsService reportInsightsService;

    @InjectMocks BusinessContextService businessContextService;

    @Test
    void buildContextForOrg_includesOrgAndCatalog() {
        when(organizationRepository.findById(TestFixtures.ORG_ID))
                .thenReturn(Optional.of(TestFixtures.organization()));
        when(reportInsightsService.inventoryOverviewForOrg(TestFixtures.ORG_ID))
                .thenReturn(Dto.InventoryReportDto.builder()
                        .totalSku(1)
                        .totalEstimatedValue(BigDecimal.TEN)
                        .build());
        when(settingsRepository.findByOrganizationId(TestFixtures.ORG_ID)).thenReturn(Optional.empty());
        when(productRepository.findLowStockByOrganizationId(TestFixtures.ORG_ID)).thenReturn(List.of());
        when(productRepository.findExpiringByOrganizationId(any(), any())).thenReturn(List.of());
        when(productRepository.findByOrganizationIdOrderByName(TestFixtures.ORG_ID))
                .thenReturn(List.of(TestFixtures.product()));

        String ctx = businessContextService.buildContextForOrg(TestFixtures.ORG_ID);

        assertTrue(ctx.contains("Organización:"));
        assertTrue(ctx.contains("Producto Test"));
    }
}
