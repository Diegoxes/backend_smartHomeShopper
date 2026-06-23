package com.smarthome.service;

import com.smarthome.entity.InventoryLot;
import com.smarthome.entity.Product;
import com.smarthome.entity.Warehouse;
import com.smarthome.repository.InventoryLotRepository;
import com.smarthome.repository.WarehouseRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryLotServiceTest {

    @Mock InventoryLotRepository lotRepository;
    @Mock WarehouseRepository warehouseRepository;
    @InjectMocks InventoryLotService inventoryLotService;

    @Test
    void addLot_savesWhenDefaultWarehouseExists() {
        Product product = TestFixtures.product();
        Warehouse wh = Warehouse.builder().id("wh-1").name("Default").build();
        when(warehouseRepository.findDefaultByOrganizationId(TestFixtures.ORG_ID)).thenReturn(Optional.of(wh));

        inventoryLotService.addLot(product, 5.0, null);

        verify(lotRepository).save(any(InventoryLot.class));
    }

    @Test
    void addLot_skipsWhenNoWarehouse() {
        Product product = TestFixtures.product();
        when(warehouseRepository.findDefaultByOrganizationId(TestFixtures.ORG_ID)).thenReturn(Optional.empty());

        inventoryLotService.addLot(product, 5.0, null);

        verifyNoInteractions(lotRepository);
    }

    @Test
    void consumeFifo_deletesEmptyLots() {
        Product product = TestFixtures.product();
        InventoryLot lot = InventoryLot.builder().id("lot-1").product(product).quantity(5.0).build();
        when(lotRepository.findByProductIdOrderByReceivedAtAsc(TestFixtures.PRODUCT_ID))
                .thenReturn(new ArrayList<>(List.of(lot)));

        inventoryLotService.consumeFifo(product, 5.0);

        verify(lotRepository).delete(lot);
    }
}
