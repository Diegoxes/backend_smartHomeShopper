package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Product;
import com.smarthome.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportExportService {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final InventoryReportInsightsService reportInsightsService;
    private final OrganizationContextService orgContext;
    private final ProductRepository productRepo;

    public byte[] exportXlsx(LocalDate from, LocalDate to) {
        return exportCompletoXlsxForOrg(orgContext.requireActiveOrgId(), from, to);
    }

    public byte[] exportInventarioXlsxForOrg(String orgId) {
        Dto.InventoryReportDto inventory = reportInsightsService.inventoryOverviewForOrg(orgId);
        List<Product> products = productRepo.findByOrganizationId(orgId);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeResumenSheet(wb, inventory);
            writeProductosSheet(wb, products);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando XLSX de inventario", e);
        }
    }

    public byte[] exportRotacionXlsxForOrg(String orgId, LocalDate from, LocalDate to) {
        Dto.RotationReportDto rotation = reportInsightsService.rotationForOrg(orgId, from, to);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeRotacionSheet(wb, rotation);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando XLSX de rotación", e);
        }
    }

    public byte[] exportCompletoXlsxForOrg(String orgId, LocalDate from, LocalDate to) {
        Dto.InventoryReportDto inventory = reportInsightsService.inventoryOverviewForOrg(orgId);
        Dto.RotationReportDto rotation = reportInsightsService.rotationForOrg(orgId, from, to);
        List<Product> products = productRepo.findByOrganizationId(orgId);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeResumenSheet(wb, inventory);
            writeProductosSheet(wb, products);
            writeRotacionSheet(wb, rotation);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando XLSX completo", e);
        }
    }

    public static String xlsxContentType() {
        return XLSX_CONTENT_TYPE;
    }

    private void writeResumenSheet(Workbook wb, Dto.InventoryReportDto inventory) {
        Sheet sheet = wb.createSheet("Resumen");
        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("Metrica");
        h.createCell(1).setCellValue("Valor");

        int r = 1;
        sheet.createRow(r).createCell(0).setCellValue("Total SKU");
        sheet.getRow(r++).createCell(1).setCellValue(inventory.getTotalSku());
        sheet.createRow(r).createCell(0).setCellValue("Valor estimado");
        sheet.getRow(r++).createCell(1).setCellValue(inventory.getTotalEstimatedValue().doubleValue());

        r++;
        Row catHeader = sheet.createRow(r++);
        catHeader.createCell(0).setCellValue("Categoria");
        catHeader.createCell(1).setCellValue("SKU");
        catHeader.createCell(2).setCellValue("Cantidad total");
        catHeader.createCell(3).setCellValue("Gasto estimado");

        for (Dto.CategoryBreakdownDto cat : inventory.getByCategory()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(cat.getCategory());
            row.createCell(1).setCellValue(cat.getSkuCount());
            row.createCell(2).setCellValue(cat.getQuantitySum());
            row.createCell(3).setCellValue(cat.getEstimatedSpend().doubleValue());
        }
    }

    private void writeProductosSheet(Workbook wb, List<Product> products) {
        Sheet sheet = wb.createSheet("Productos");
        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("Nombre");
        h.createCell(1).setCellValue("SKU");
        h.createCell(2).setCellValue("Cantidad");
        h.createCell(3).setCellValue("Unidad");
        h.createCell(4).setCellValue("Minimo");
        h.createCell(5).setCellValue("Categoria");
        h.createCell(6).setCellValue("Stock bajo");

        int r = 1;
        for (Product p : products) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(p.getName());
            row.createCell(1).setCellValue(Optional.ofNullable(p.getSku()).orElse(""));
            row.createCell(2).setCellValue(p.getQuantity() != null ? p.getQuantity() : 0d);
            row.createCell(3).setCellValue(p.getUnit().name().toLowerCase(Locale.ROOT));
            row.createCell(4).setCellValue(p.getMinQuantity() != null ? p.getMinQuantity() : 0d);
            row.createCell(5).setCellValue(Optional.ofNullable(p.getCategory()).orElse("Sin categoría"));
            row.createCell(6).setCellValue(p.isLowStock() ? "Si" : "No");
        }
    }

    private void writeRotacionSheet(Workbook wb, Dto.RotationReportDto rotation) {
        Sheet sheet = wb.createSheet("Rotacion");
        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("Producto");
        h.createCell(1).setCellValue("Categoria");
        h.createCell(2).setCellValue("Consumido");
        h.createCell(3).setCellValue("Promedio diario");
        h.createCell(4).setCellValue("Dias restantes est.");
        h.createCell(5).setCellValue("Velocidad");

        int r = 1;
        for (Dto.RotationReportRowDto rowDto : rotation.getRows()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(rowDto.getProductName());
            row.createCell(1).setCellValue(rowDto.getCategory());
            row.createCell(2).setCellValue(rowDto.getUnitsConsumed());
            if (rowDto.getAvgDailyConsumption() != null) {
                row.createCell(3).setCellValue(rowDto.getAvgDailyConsumption());
            }
            if (rowDto.getEstimatedDaysRemaining() != null) {
                row.createCell(4).setCellValue(rowDto.getEstimatedDaysRemaining());
            }
            row.createCell(5).setCellValue(rowDto.getVelocity());
        }
    }
}
