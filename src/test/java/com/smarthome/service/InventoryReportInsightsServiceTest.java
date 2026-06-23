package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.repository.ConsumptionLogRepository;
import com.smarthome.repository.OrganizationSettingsRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.PurchaseRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReportInsightsServiceTest {

    @Mock ProductRepository productRepo;
    @Mock ConsumptionLogRepository consumptionLogRepo;
    @Mock PurchaseRepository purchaseRepo;
    @Mock OrganizationContextService orgContext;
    @Mock OrganizationSettingsRepository settingsRepository;
    @InjectMocks InventoryReportInsightsService inventoryReportInsightsService;

    @Test
    void rotationForOrg_emptyProducts_returnsEmptyRows() {
        when(settingsRepository.findByOrganizationId(TestFixtures.ORG_ID))
                .thenReturn(Optional.of(TestFixtures.orgSettings()));
        when(productRepo.findByOrganizationId(TestFixtures.ORG_ID)).thenReturn(List.of());
        when(consumptionLogRepo.findForOrganizationBetween(any(), any(), any(), any())).thenReturn(List.of());

        Dto.RotationReportDto report = inventoryReportInsightsService.rotationForOrg(
                TestFixtures.ORG_ID, null, null);

        assertNotNull(report);
        assertTrue(report.getRows().isEmpty());
    }

    @Test
    void inventoryOverviewForOrg_countsProducts() {
        when(productRepo.findByOrganizationId(TestFixtures.ORG_ID))
                .thenReturn(List.of(TestFixtures.product()));
        when(purchaseRepo.findFirstByProductIdOrderByPurchasedAtDesc(any())).thenReturn(Optional.empty());
        when(consumptionLogRepo.findForOrganizationBetween(any(), any(), any(), any())).thenReturn(List.of());
        when(consumptionLogRepo.findDistinctConsumedProductIdsSince(any(), any(), any())).thenReturn(List.of());

        Dto.InventoryReportDto overview =
                inventoryReportInsightsService.inventoryOverviewForOrg(TestFixtures.ORG_ID);

        assertEquals(1, overview.getTotalSku());
    }

    @Test
    void rotation_delegatesToOrgContext() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(settingsRepository.findByOrganizationId(TestFixtures.ORG_ID))
                .thenReturn(Optional.of(TestFixtures.orgSettings()));
        when(productRepo.findByOrganizationId(TestFixtures.ORG_ID)).thenReturn(List.of());
        when(consumptionLogRepo.findForOrganizationBetween(any(), any(), any(), any())).thenReturn(List.of());

        inventoryReportInsightsService.rotation(TestFixtures.USER_ID, null, null);

        verify(orgContext).requireActiveOrgId();
    }
}
