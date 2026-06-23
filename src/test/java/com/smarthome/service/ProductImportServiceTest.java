package com.smarthome.service;

import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.UserRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductImportServiceTest {

    @Mock ProductRepository productRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock UserRepository userRepository;
    @Mock OrganizationContextService orgContext;
    @Mock AuditService auditService;
    @InjectMocks ProductImportService productImportService;

    @Test
    void preview_validCsv_countsRows() {
        String csv = "sku,name,qty,min\nSKU-1,Prod,10,5\nSKU-2,Prod2,3,1\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        var preview = productImportService.preview(file);

        assertEquals(2, preview.getValidRows());
        assertEquals(0, preview.getInvalidRows());
    }

    @Test
    void preview_invalidRow_countsInvalid() {
        String csv = "sku,name,qty,min\nbadrow\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        var preview = productImportService.preview(file);

        assertEquals(0, preview.getValidRows());
        assertEquals(1, preview.getInvalidRows());
    }

    @Test
    void commit_duplicateSku_countsInvalid() throws Exception {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(orgContext.requireUserId()).thenReturn(TestFixtures.USER_ID);
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(TestFixtures.organization()));
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(TestFixtures.user()));
        when(productRepository.findByOrganizationIdAndSku(TestFixtures.ORG_ID, "SKU-1"))
                .thenReturn(Optional.of(TestFixtures.product()));

        String csv = "sku,name,qty,min\nSKU-1,Prod,10,5\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        var result = productImportService.commit(file);

        assertEquals(0, result.getValidRows());
        assertEquals(1, result.getInvalidRows());
        verify(auditService).log("PRODUCT_IMPORT", "Organization", TestFixtures.ORG_ID);
    }
}
