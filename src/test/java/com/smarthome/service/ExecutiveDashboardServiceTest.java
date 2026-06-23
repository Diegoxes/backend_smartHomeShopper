package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Product;
import com.smarthome.repository.OrganizationSettingsRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.PurchaseRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutiveDashboardServiceTest {

    @Mock ProductRepository productRepo;
    @Mock PurchaseRepository purchaseRepo;
    @Mock OrganizationContextService orgContext;
    @Mock OrganizationSettingsRepository settingsRepository;
    @Mock InventoryReportInsightsService reportInsightsService;
    @InjectMocks ExecutiveDashboardService executiveDashboardService;

    @Test
    void executive_aggregatesKpis() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(orgContext.requireUserId()).thenReturn(TestFixtures.USER_ID);
        when(settingsRepository.findByOrganizationId(TestFixtures.ORG_ID))
                .thenReturn(Optional.of(TestFixtures.orgSettings()));
        Product p = TestFixtures.product();
        p.setAvgCost(BigDecimal.TEN);
        when(productRepo.findByOrganizationId(TestFixtures.ORG_ID)).thenReturn(List.of(p));
        when(purchaseRepo.sumTotalInRange(any(), any(), any(), any())).thenReturn(BigDecimal.valueOf(50));
        when(reportInsightsService.inventoryOverview(TestFixtures.USER_ID))
                .thenReturn(Dto.InventoryReportDto.builder().build());

        Dto.ExecutiveDashboardDto dto = executiveDashboardService.executive();

        assertEquals(BigDecimal.valueOf(100).setScale(2), dto.getTotalStockValue());
        assertEquals(0, dto.getLowStockCount());
    }
}
