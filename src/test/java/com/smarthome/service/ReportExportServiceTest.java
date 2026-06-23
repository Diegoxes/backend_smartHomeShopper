package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.repository.ProductRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportExportServiceTest {

    @Mock InventoryReportInsightsService reportInsightsService;
    @Mock OrganizationContextService orgContext;
    @Mock ProductRepository productRepo;
    @InjectMocks ReportExportService reportExportService;

    @Test
    void exportInventarioXlsxForOrg_returnsNonEmptyBytes() {
        when(reportInsightsService.inventoryOverviewForOrg(TestFixtures.ORG_ID))
                .thenReturn(Dto.InventoryReportDto.builder()
                        .totalSku(1)
                        .totalEstimatedValue(BigDecimal.ZERO)
                        .byCategory(List.of())
                        .build());
        when(productRepo.findByOrganizationId(TestFixtures.ORG_ID)).thenReturn(List.of(TestFixtures.product()));

        byte[] data = reportExportService.exportInventarioXlsxForOrg(TestFixtures.ORG_ID);

        assertNotNull(data);
        assertTrue(data.length > 100);
    }

    @Test
    void xlsxContentType_returnsMimeType() {
        assertTrue(ReportExportService.xlsxContentType().contains("spreadsheetml"));
    }

    @Test
    void exportXlsx_usesOrgContext() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(reportInsightsService.inventoryOverviewForOrg(TestFixtures.ORG_ID))
                .thenReturn(Dto.InventoryReportDto.builder()
                        .totalEstimatedValue(BigDecimal.ZERO)
                        .byCategory(List.of())
                        .build());
        when(reportInsightsService.rotationForOrg(eq(TestFixtures.ORG_ID), any(), any()))
                .thenReturn(Dto.RotationReportDto.builder().rows(List.of()).build());
        when(productRepo.findByOrganizationId(TestFixtures.ORG_ID)).thenReturn(List.of());

        byte[] data = reportExportService.exportXlsx(null, null);

        assertNotNull(data);
        verify(orgContext).requireActiveOrgId();
    }
}
