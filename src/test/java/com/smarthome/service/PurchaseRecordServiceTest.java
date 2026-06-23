package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Purchase;
import com.smarthome.repository.MeasureUnitRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.PurchaseRepository;
import com.smarthome.repository.SupplierRepository;
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
class PurchaseRecordServiceTest {

    @Mock PurchaseRepository purchaseRepo;
    @Mock ProductRepository productRepo;
    @Mock SupplierRepository supplierRepo;
    @Mock OrganizationContextService orgContext;
    @Mock MeasureUnitRepository measureUnitRepository;
    @InjectMocks PurchaseRecordService purchaseRecordService;

    @Test
    void createManual_productNotFound_throws() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(productRepo.findByIdAndOrganizationId(anyString(), eq(TestFixtures.ORG_ID))).thenReturn(Optional.empty());

        Dto.CreatePurchaseRequest req = new Dto.CreatePurchaseRequest();
        req.setProductId(TestFixtures.PRODUCT_ID);
        req.setQuantity(2.0);
        req.setUnitPrice(BigDecimal.TEN);

        assertThrows(RuntimeException.class,
                () -> purchaseRecordService.createManual(TestFixtures.USER_ID, req));
    }

    @Test
    void attachInitialStockIfPriced_noOpWhenNoCost() {
        purchaseRecordService.attachInitialStockIfPriced(
                TestFixtures.product(), TestFixtures.ORG_ID, 5.0, null, null, "MXN");
        verifyNoInteractions(purchaseRepo);
    }

    @Test
    void attachInitialStockIfPriced_savesPurchaseWhenPriced() {
        when(purchaseRepo.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        purchaseRecordService.attachInitialStockIfPriced(
                TestFixtures.product(), TestFixtures.ORG_ID, 10.0, BigDecimal.valueOf(3.5), null, "MXN");

        verify(purchaseRepo).save(any(Purchase.class));
    }

    @Test
    void attachToRestockIfPriced_savesStockUnitsNotBoxes() {
        Dto.ConsumeRequest req = TestFixtures.consumeRequest(2.0);
        req.setUnitPrice(BigDecimal.valueOf(4.5));

        when(purchaseRepo.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        purchaseRecordService.attachToRestockIfPriced(
                TestFixtures.product(), TestFixtures.ORG_ID, req, 48.0, "MXN");

        verify(purchaseRepo).save(argThat(pu ->
                pu.getQuantity() == 48.0
                        && pu.getUnitPrice().compareTo(BigDecimal.valueOf(4.5)) == 0
                        && pu.getTotalAmount().compareTo(BigDecimal.valueOf(216.0)) == 0));
    }

    @Test
    void attachToRestockIfPriced_noOpWhenNoPrice() {
        purchaseRecordService.attachToRestockIfPriced(
                TestFixtures.product(), TestFixtures.ORG_ID, TestFixtures.consumeRequest(1.0), 24.0, "MXN");
        verifyNoInteractions(purchaseRepo);
    }

    @Test
    void attachToRestockIfPriced_savesPurchaseWhenPriced() {
        Dto.ConsumeRequest req = TestFixtures.consumeRequest(2.0);
        req.setUnitPrice(BigDecimal.valueOf(15));

        when(purchaseRepo.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        purchaseRecordService.attachToRestockIfPriced(
                TestFixtures.product(), TestFixtures.ORG_ID, req, 48.0, "MXN");

        verify(purchaseRepo).save(any(Purchase.class));
    }

    @Test
    void listFiltered_returnsPage() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(purchaseRepo.sumTotalInRange(any(), any(), any(), any())).thenReturn(BigDecimal.valueOf(100));
        when(purchaseRepo.findFiltered(any(), any(), any(), any())).thenReturn(List.of());

        Dto.PurchasesPageDto page = purchaseRecordService.listFiltered(TestFixtures.USER_ID, null, null, null);

        assertNotNull(page);
        assertEquals(0, page.getItems().size());
        assertEquals(BigDecimal.valueOf(100).setScale(2), page.getPeriodTotalSpend());
    }
}
