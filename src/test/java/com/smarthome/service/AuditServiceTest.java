package com.smarthome.service;

import com.smarthome.entity.AuditLog;
import com.smarthome.repository.AuditLogRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditLogRepository auditLogRepository;
    @Mock OrganizationContextService orgContext;
    @InjectMocks AuditService auditService;

    @Test
    void log_persistsAuditEntry() {
        when(orgContext.requireUserId()).thenReturn(TestFixtures.USER_ID);
        when(orgContext.requireActiveOrgId()).thenReturn(TestFixtures.ORG_ID);

        auditService.log("CREATE", "Product", TestFixtures.PRODUCT_ID);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertEquals(TestFixtures.USER_ID, saved.getUserId());
        assertEquals(TestFixtures.ORG_ID, saved.getOrganizationId());
        assertEquals("CREATE", saved.getAction());
    }

    @Test
    void log_swallowsContextErrors() {
        when(orgContext.requireUserId()).thenThrow(new RuntimeException("no auth"));
        auditService.log("CREATE", "Product", TestFixtures.PRODUCT_ID);
        verifyNoInteractions(auditLogRepository);
    }
}
