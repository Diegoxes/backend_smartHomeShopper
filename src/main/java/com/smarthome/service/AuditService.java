package com.smarthome.service;

import com.smarthome.entity.AuditLog;
import com.smarthome.entity.ConsumptionLog;
import com.smarthome.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final OrganizationContextService orgContext;

    @Transactional
    public void log(String action, String entityType, String entityId) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .userId(orgContext.requireUserId())
                    .organizationId(orgContext.requireActiveOrgId())
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .channel(ConsumptionLog.Source.WEB)
                    .build());
        } catch (Exception ignored) {
        }
    }
}
