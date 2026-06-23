package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Organization;
import com.smarthome.entity.Product;
import com.smarthome.entity.StockLevel;
import com.smarthome.entity.Warehouse;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.StockLevelRepository;
import com.smarthome.repository.WarehouseRepository;
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
class WarehouseServiceTest {

    @Mock WarehouseRepository warehouseRepository;
    @Mock StockLevelRepository stockLevelRepository;
    @Mock ProductRepository productRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationContextService orgContext;
    @Mock AuditService auditService;
    @InjectMocks WarehouseService warehouseService;

    @Test
    void create_success() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(TestFixtures.organization()));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> {
            Warehouse w = inv.getArgument(0);
            w.setId("wh-1");
            return w;
        });

        Dto.CreateWarehouseRequest req = new Dto.CreateWarehouseRequest();
        req.setName("Secundario");

        Dto.WarehouseDto dto = warehouseService.create(req);

        assertEquals("Secundario", dto.getName());
        assertFalse(dto.isDefault());
        verify(auditService).log("WAREHOUSE_CREATE", "Warehouse", "wh-1");
    }

    @Test
    void transfer_insufficientStock_throws() {
        Warehouse from = Warehouse.builder().id("wh-from").build();
        Warehouse to = Warehouse.builder().id("wh-to").build();
        Product product = TestFixtures.product();
        StockLevel fromSl = StockLevel.builder().quantity(1.0).build();

        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(warehouseRepository.findByIdAndOrganizationId("wh-from", TestFixtures.ORG_ID)).thenReturn(Optional.of(from));
        when(warehouseRepository.findByIdAndOrganizationId("wh-to", TestFixtures.ORG_ID)).thenReturn(Optional.of(to));
        when(productRepository.findByIdAndOrganizationId(TestFixtures.PRODUCT_ID, TestFixtures.ORG_ID))
                .thenReturn(Optional.of(product));
        when(stockLevelRepository.findByProductIdAndWarehouseId(TestFixtures.PRODUCT_ID, "wh-from"))
                .thenReturn(Optional.of(fromSl));

        Dto.TransferStockRequest req = new Dto.TransferStockRequest();
        req.setFromWarehouseId("wh-from");
        req.setToWarehouseId("wh-to");
        req.setProductId(TestFixtures.PRODUCT_ID);
        req.setQuantity(5.0);

        assertThrows(RuntimeException.class, () -> warehouseService.transfer(req));
    }

    @Test
    void list_returnsWarehouses() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(warehouseRepository.findByOrganizationIdOrderByNameAsc(TestFixtures.ORG_ID))
                .thenReturn(List.of(Warehouse.builder().id("wh-1").name("Main").isDefault(true).build()));

        assertEquals(1, warehouseService.list().size());
    }
}
