package com.smarthome.service;

import com.smarthome.entity.WhatsAppReportDownload;
import com.smarthome.repository.WhatsAppReportDownloadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WhatsAppReportTokenService {

    public record StoredReport(String token, String mediaUrl) {}

    private final WhatsAppReportDownloadRepository repository;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    public boolean isPublicBaseUrlConfigured() {
        return publicBaseUrl != null && !publicBaseUrl.isBlank();
    }

    @Transactional
    public Optional<StoredReport> store(String orgId, String fileName, byte[] data) {
        if (!isPublicBaseUrlConfigured()) {
            return Optional.empty();
        }
        purgeExpired();

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

        repository.save(WhatsAppReportDownload.builder()
                .token(token)
                .organizationId(orgId)
                .fileName(fileName)
                .contentType(ReportExportService.xlsxContentType())
                .data(data)
                .expiresAt(expiresAt)
                .build());

        String base = publicBaseUrl.replaceAll("/+$", "");
        String mediaUrl = base + "/api/webhook/reports/" + token;
        return Optional.of(new StoredReport(token, mediaUrl));
    }

    @Transactional(readOnly = true)
    public Optional<WhatsAppReportDownload> load(String token) {
        return repository.findByTokenAndExpiresAtAfter(token, LocalDateTime.now());
    }

    @Transactional
    public void purgeExpired() {
        repository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void scheduledPurge() {
        purgeExpired();
    }
}
