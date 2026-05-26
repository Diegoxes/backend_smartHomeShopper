package com.smarthome.service;

import com.smarthome.dto.Dto;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final InventoryReportInsightsService reportInsightsService;
    private final OrganizationContextService orgContext;

    public byte[] exportXlsx(LocalDate from, LocalDate to) {
        String userId = orgContext.requireUserId();
        Dto.RotationReportDto rotation = reportInsightsService.rotation(userId, from, to);
        Dto.InventoryReportDto inventory = reportInsightsService.inventoryOverview(userId);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet rot = wb.createSheet("Rotacion");
            Row h1 = rot.createRow(0);
            h1.createCell(0).setCellValue("Producto");
            h1.createCell(1).setCellValue("Categoria");
            h1.createCell(2).setCellValue("Consumido");
            h1.createCell(3).setCellValue("Velocidad");
            int r = 1;
            for (Dto.RotationReportRowDto row : rotation.getRows()) {
                Row rr = rot.createRow(r++);
                rr.createCell(0).setCellValue(row.getProductName());
                rr.createCell(1).setCellValue(row.getCategory());
                rr.createCell(2).setCellValue(row.getUnitsConsumed());
                rr.createCell(3).setCellValue(row.getVelocity());
            }

            Sheet inv = wb.createSheet("Inventario");
            Row h2 = inv.createRow(0);
            h2.createCell(0).setCellValue("Metrica");
            h2.createCell(1).setCellValue("Valor");
            inv.createRow(1).createCell(0).setCellValue("Total SKU");
            inv.getRow(1).createCell(1).setCellValue(inventory.getTotalSku());
            inv.createRow(2).createCell(0).setCellValue("Valor estimado");
            inv.getRow(2).createCell(1).setCellValue(inventory.getTotalEstimatedValue().doubleValue());

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando XLSX", e);
        }
    }
}
