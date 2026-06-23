package com.smarthome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthome.entity.ConsumptionLog;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.entity.Product;
import com.smarthome.repository.ConsumptionLogRepository;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppInventoryActionServiceTest {

    @Mock ProductRepository productRepo;
    @Mock ConsumptionLogRepository logRepo;
    @Mock OrganizationMemberRepository memberRepository;
    @Mock InventoryLotService inventoryLotService;
    @Mock ProductUomService productUomService;
    @Mock OrganizationContextService orgContext;
    @InjectMocks WhatsAppInventoryActionService whatsAppInventoryActionService;

    @Test
    void consume_reducesQuantityAndLogs() {
        Product product = TestFixtures.product();
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        whatsAppInventoryActionService.consume(TestFixtures.user(), product, 2.0);

        assertEquals(8.0, product.getQuantity());
        verify(inventoryLotService).consumeFifo(product, 2.0);
        verify(logRepo).save(argThat(log ->
                log.getActionType() == ConsumptionLog.ActionType.CONSUMED
                        && log.getSource() == ConsumptionLog.Source.WHATSAPP));
    }

    @Test
    void addOrRestock_existingProduct_incrementsQuantity() {
        Product product = TestFixtures.product();
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        whatsAppInventoryActionService.addOrRestock(
                TestFixtures.user(), Product.UnitType.UNIT, 5.0, null, product);

        assertEquals(15.0, product.getQuantity());
        verify(inventoryLotService).addLot(product, 5.0, product.getExpiryDate());
    }

    @Test
    void addOrRestock_newProduct_requiresMembership() {
        when(memberRepository.findByUserId(TestFixtures.USER_ID)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> whatsAppInventoryActionService.addOrRestock(
                        TestFixtures.user(), Product.UnitType.UNIT, 1.0, "Nuevo", null));
    }

    @Test
    void addOrRestock_newProduct_createsProduct() {
        OrganizationMember member = OrganizationMember.builder()
                .organization(TestFixtures.organization())
                .user(TestFixtures.user())
                .build();
        when(memberRepository.findByUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(member));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        whatsAppInventoryActionService.addOrRestock(
                TestFixtures.user(), Product.UnitType.UNIT, 3.0, "Nuevo Item", null);

        verify(productRepo).save(any(Product.class));
        verify(logRepo).save(any(ConsumptionLog.class));
    }
}
