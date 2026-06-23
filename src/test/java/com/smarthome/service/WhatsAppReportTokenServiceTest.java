package com.smarthome.service;

import com.smarthome.entity.WhatsAppReportDownload;
import com.smarthome.repository.WhatsAppReportDownloadRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppReportTokenServiceTest {

    @Mock WhatsAppReportDownloadRepository repository;
    @InjectMocks WhatsAppReportTokenService whatsAppReportTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(whatsAppReportTokenService, "publicBaseUrl", "https://app.example.com");
    }

    @Test
    void isPublicBaseUrlConfigured_trueWhenSet() {
        assertTrue(whatsAppReportTokenService.isPublicBaseUrlConfigured());
    }

    @Test
    void store_returnsMediaUrl() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<WhatsAppReportTokenService.StoredReport> stored =
                whatsAppReportTokenService.store(TestFixtures.ORG_ID, "report.xlsx", new byte[]{1, 2, 3});

        assertTrue(stored.isPresent());
        assertTrue(stored.get().mediaUrl().contains("/api/webhook/reports/"));
        verify(repository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }

    @Test
    void store_withoutBaseUrl_returnsEmpty() {
        ReflectionTestUtils.setField(whatsAppReportTokenService, "publicBaseUrl", "");

        Optional<WhatsAppReportTokenService.StoredReport> stored =
                whatsAppReportTokenService.store(TestFixtures.ORG_ID, "report.xlsx", new byte[]{1});

        assertTrue(stored.isEmpty());
    }

    @Test
    void load_delegatesToRepository() {
        WhatsAppReportDownload download = WhatsAppReportDownload.builder().token("tok").build();
        when(repository.findByTokenAndExpiresAtAfter(eq("tok"), any())).thenReturn(Optional.of(download));

        assertTrue(whatsAppReportTokenService.load("tok").isPresent());
    }
}
