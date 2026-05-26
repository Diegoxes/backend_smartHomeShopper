package com.smarthome.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthome.dto.Dto;
import com.smarthome.entity.InventorySnapshot;
import com.smarthome.entity.Organization;
import com.smarthome.repository.InventorySnapshotRepository;
import com.smarthome.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventorySnapshotService {

    private final OrganizationRepository organizationRepository;
    private final InventoryReportInsightsService reportInsightsService;
    private final InventorySnapshotRepository snapshotRepository;
    private final OrganizationContextService orgContext;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "0 0 23 * * *")
    @Transactional
    public void captureDailySnapshots() {
        for (Organization org : organizationRepository.findAll()) {
            try {
                Dto.InventoryReportDto report = reportInsightsService.inventoryOverviewForOrg(org.getId());
                snapshotRepository.save(InventorySnapshot.builder()
                        .organization(org)
                        .snapshotDate(LocalDate.now())
                        .totalValue(report.getTotalEstimatedValue())
                        .breakdownJson(objectMapper.writeValueAsString(report.getByCategory()))
                        .build());
            } catch (Exception e) {
                log.warn("Snapshot org {}: {}", org.getId(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Dto.InventorySnapshotDto> history(LocalDate from, LocalDate to) {
        String orgId = orgContext.requireOrgId();
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        return snapshotRepository.findByOrganizationIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(orgId, start, end)
                .stream()
                .map(s -> Dto.InventorySnapshotDto.builder()
                        .date(s.getSnapshotDate())
                        .totalValue(s.getTotalValue())
                        .breakdownJson(s.getBreakdownJson())
                        .build())
                .collect(Collectors.toList());
    }
}
