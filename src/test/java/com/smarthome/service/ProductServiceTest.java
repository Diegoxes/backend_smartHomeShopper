package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.*;
import com.smarthome.repository.*;
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
class ProductServiceTest {

    @Mock ProductRepository productRepo;
    @Mock ConsumptionLogRepository logRepo;
    @Mock PurchaseRepository purchaseRepo;
    @Mock UserRepository userRepo;
    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationSettingsRepository settingsRepository;
    @Mock OrganizationContextService orgContext;
    @Mock PurchaseRecordService purchaseRecordService;
    @Mock StockLevelRepository stockLevelRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock InventoryLotService inventoryLotService;
    @Mock ProductUomService productUomService;
    @Mock MeasureUnitService measureUnitService;
    @InjectMocks ProductService productService;

    @Test
    void create_duplicateSku_throws() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(productRepo.findByOrganizationIdAndSku(TestFixtures.ORG_ID, TestFixtures.SKU))
                .thenReturn(Optional.of(TestFixtures.product()));

        assertThrows(RuntimeException.class,
                () -> productService.create(TestFixtures.USER_ID, TestFixtures.createProductRequest()));
    }

    @Test
    void create_success() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(productRepo.findByOrganizationIdAndSku(TestFixtures.ORG_ID, TestFixtures.SKU)).thenReturn(Optional.empty());
        when(userRepo.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(TestFixtures.user()));
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(TestFixtures.organization()));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(TestFixtures.PRODUCT_ID);
            return p;
        });
        when(settingsRepository.findByOrganizationId(TestFixtures.ORG_ID))
                .thenReturn(Optional.of(TestFixtures.orgSettings()));
        when(warehouseRepository.findDefaultByOrganizationId(TestFixtures.ORG_ID)).thenReturn(Optional.empty());
        when(productUomService.listForProduct(anyString())).thenReturn(List.of());
        when(productUomService.stockBreakdown(any())).thenReturn(List.of());
        when(productUomService.stockDisplay(any())).thenReturn("0 unidades");

        Dto.ProductResponse response = productService.create(TestFixtures.USER_ID, TestFixtures.createProductRequest());

        assertEquals(TestFixtures.SKU, response.getSku());
        verify(productRepo).save(any(Product.class));
        verify(purchaseRecordService).attachInitialStockIfPriced(
                any(Product.class), eq(TestFixtures.ORG_ID), anyDouble(), any(), any(), eq("MXN"));
    }

    @Test
    void getById_notFound_throws() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(productRepo.findByIdAndOrganizationId(TestFixtures.PRODUCT_ID, TestFixtures.ORG_ID))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> productService.getById(TestFixtures.PRODUCT_ID, TestFixtures.USER_ID));
    }

    @Test
    void consume_reducesQuantityAndLogs() {
        Product product = TestFixtures.product();
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(productRepo.findByIdAndOrganizationId(TestFixtures.PRODUCT_ID, TestFixtures.ORG_ID))
                .thenReturn(Optional.of(product));
        when(settingsRepository.findByOrganizationId(TestFixtures.ORG_ID))
                .thenReturn(Optional.of(TestFixtures.orgSettings()));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(warehouseRepository.findDefaultByOrganizationId(TestFixtures.ORG_ID)).thenReturn(Optional.empty());
        when(logRepo.avgDailyConsumption(any(), any(), anyInt(), any())).thenReturn(null);

        Dto.ProductResponse response = productService.consume(
                TestFixtures.PRODUCT_ID, TestFixtures.USER_ID, TestFixtures.consumeRequest(3.0));

        assertEquals(7.0, response.getQuantity());
        verify(inventoryLotService).consumeFifo(product, 3.0);
        verify(logRepo).save(any(ConsumptionLog.class));
    }

    @Test
    void getDashboard_countsLowStockAndExpiring() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(settingsRepository.findByOrganizationId(TestFixtures.ORG_ID))
                .thenReturn(Optional.of(TestFixtures.orgSettings()));
        when(productRepo.findByOrganizationId(TestFixtures.ORG_ID))
                .thenReturn(List.of(TestFixtures.product(), TestFixtures.lowStockProduct(), TestFixtures.expiringProduct()));
        when(logRepo.avgDailyConsumption(any(), any(), anyInt(), any())).thenReturn(null);

        Dto.DashboardResponse dashboard = productService.getDashboard(TestFixtures.USER_ID);

        assertEquals(3, dashboard.getTotalProducts());
        assertEquals(1, dashboard.getLowStockCount());
        assertEquals(1, dashboard.getExpiringCount());
    }
}
