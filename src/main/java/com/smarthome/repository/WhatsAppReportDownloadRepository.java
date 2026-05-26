package com.smarthome.repository;

import com.smarthome.entity.WhatsAppReportDownload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface WhatsAppReportDownloadRepository extends JpaRepository<WhatsAppReportDownload, String> {

    Optional<WhatsAppReportDownload> findByTokenAndExpiresAtAfter(String token, LocalDateTime now);

    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
