package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.ExecutiveDashboardService;
import com.smarthome.service.InventoryReportInsightsService;
import com.smarthome.service.InventorySnapshotService;
import com.smarthome.service.ReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportInsightsController {

    private final InventoryReportInsightsService reportInsightsService;
    private final ReportExportService reportExportService;
    private final InventorySnapshotService snapshotService;

    @GetMapping("/rotation")
    @PreAuthorize("hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public Dto.RotationReportDto rotation(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reportInsightsService.rotation(userId, from, to);
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public Dto.InventoryReportDto inventory(@AuthenticationPrincipal String userId) {
        return reportInsightsService.inventoryOverview(userId);
    }

    @GetMapping("/by-category")
    @PreAuthorize("hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public List<Dto.CategoryBreakdownDto> byCategory(@AuthenticationPrincipal String userId) {
        return reportInsightsService.byCategory(userId);
    }

    @GetMapping("/by-supplier")
    @PreAuthorize("hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public List<Dto.SupplierSpendRowDto> bySupplier(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reportInsightsService.bySupplier(userId, from, to);
    }

    @GetMapping("/by-channel")
    @PreAuthorize("hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public List<Dto.ChannelReportRowDto> byChannel(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reportInsightsService.byChannel(userId, from, to);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public List<Dto.InventorySnapshotDto> history(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return snapshotService.history(from, to);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "xlsx") String format) {
        if (!"xlsx".equalsIgnoreCase(format)) {
            return ResponseEntity.badRequest().build();
        }
        byte[] data = reportExportService.exportXlsx(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-inventario.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}
