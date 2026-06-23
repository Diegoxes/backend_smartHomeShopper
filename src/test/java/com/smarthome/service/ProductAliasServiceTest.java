package com.smarthome.service;

import com.smarthome.entity.ProductAlias;
import com.smarthome.repository.ProductAliasRepository;
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
class ProductAliasServiceTest {

    @Mock ProductAliasRepository aliasRepo;
    @Mock ProductRepository productRepo;
    @Mock OrganizationContextService orgContext;
    @InjectMocks ProductAliasService productAliasService;

    @Test
    void assertProductOwned_foreignProduct_throws() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(productRepo.findByIdAndOrganizationId(TestFixtures.PRODUCT_ID, TestFixtures.ORG_ID))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> productAliasService.assertProductOwned(TestFixtures.PRODUCT_ID));
    }

    @Test
    void addManual_shortAlias_throws() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(productRepo.findByIdAndOrganizationId(TestFixtures.PRODUCT_ID, TestFixtures.ORG_ID))
                .thenReturn(Optional.of(TestFixtures.product()));
        when(productRepo.findById(TestFixtures.PRODUCT_ID)).thenReturn(Optional.of(TestFixtures.product()));

        assertThrows(IllegalArgumentException.class,
                () -> productAliasService.addManual(TestFixtures.PRODUCT_ID, TestFixtures.USER_ID, "a"));
    }

    @Test
    void addManual_success() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(productRepo.findByIdAndOrganizationId(TestFixtures.PRODUCT_ID, TestFixtures.ORG_ID))
                .thenReturn(Optional.of(TestFixtures.product()));
        when(productRepo.findById(TestFixtures.PRODUCT_ID)).thenReturn(Optional.of(TestFixtures.product()));
        when(aliasRepo.existsByProductIdAndNormalizedAlias(anyString(), anyString())).thenReturn(false);
        when(aliasRepo.save(any(ProductAlias.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = productAliasService.addManual(TestFixtures.PRODUCT_ID, TestFixtures.USER_ID, "leche entera");

        assertEquals("leche entera", dto.getAliasText());
        verify(aliasRepo).save(any(ProductAlias.class));
    }
}
