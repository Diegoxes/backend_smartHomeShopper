package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Supplier;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.SupplierRepository;
import com.smarthome.repository.UserRepository;
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
class SupplierManagementServiceTest {

    @Mock SupplierRepository supplierRepo;
    @Mock UserRepository userRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationContextService orgContext;
    @InjectMocks SupplierManagementService supplierManagementService;

    @Test
    void list_returnsSuppliersForOrg() {
        Supplier s = Supplier.builder().id("s1").name("Proveedor").build();
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(supplierRepo.listAllForOrganization(TestFixtures.ORG_ID)).thenReturn(List.of(s));

        List<Dto.SupplierDto> result = supplierManagementService.list(TestFixtures.USER_ID);

        assertEquals(1, result.size());
        assertEquals("Proveedor", result.get(0).getName());
    }

    @Test
    void create_success() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(TestFixtures.user()));
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(TestFixtures.organization()));
        when(supplierRepo.save(any(Supplier.class))).thenAnswer(inv -> {
            Supplier sup = inv.getArgument(0);
            sup.setId("s1");
            return sup;
        });

        Dto.CreateSupplierRequest req = new Dto.CreateSupplierRequest();
        req.setName("Nuevo Proveedor");

        Dto.SupplierDto dto = supplierManagementService.create(TestFixtures.USER_ID, req);

        assertEquals("Nuevo Proveedor", dto.getName());
    }

    @Test
    void delete_notFound_throws() {
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);
        when(supplierRepo.findOwned("s1", TestFixtures.ORG_ID)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> supplierManagementService.delete(TestFixtures.USER_ID, "s1"));
    }
}
