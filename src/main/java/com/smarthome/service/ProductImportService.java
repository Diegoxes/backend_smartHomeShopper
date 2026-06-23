package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Organization;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImportService {

    private final ProductRepository productRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationContextService orgContext;
    private final AuditService auditService;
    private final ProductUomService productUomService;
    private final MeasureUnitService measureUnitService;

    public Dto.ProductImportPreviewDto preview(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int valid = 0;
        int invalid = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int row = 0;
            while ((line = br.readLine()) != null) {
                row++;
                if (row == 1 && line.toLowerCase().contains("sku")) continue;
                String[] parts = line.split(",");
                if (parts.length < 4) {
                    invalid++;
                    errors.add("Fila " + row + ": columnas insuficientes");
                    continue;
                }
                valid++;
            }
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
        return Dto.ProductImportPreviewDto.builder()
                .validRows(valid).invalidRows(invalid).errors(errors).build();
    }

    @Transactional
    public Dto.ProductImportPreviewDto commit(MultipartFile file) {
        String orgId = orgContext.requireActiveOrgId();
        String userId = orgContext.requireUserId();
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();

        List<String> errors = new ArrayList<>();
        int valid = 0;
        int invalid = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int row = 0;
            while ((line = br.readLine()) != null) {
                row++;
                if (row == 1 && line.toLowerCase().contains("sku")) continue;
                String[] p = line.split(",");
                if (p.length < 4) {
                    invalid++;
                    continue;
                }
                String sku = p[0].trim();
                if (productRepository.findByOrganizationIdAndSku(orgId, sku).isPresent()) {
                    invalid++;
                    errors.add("SKU duplicado: " + sku);
                    continue;
                }
                Product prod = Product.builder()
                        .organization(org)
                        .user(user)
                        .sku(sku)
                        .name(p[1].trim())
                        .quantity(parseDouble(p[2], 0))
                        .minQuantity(parseDouble(p[3], 1))
                        .unit(Product.UnitType.UNIT)
                        .consumptionPerUse(1.0)
                        .category(p.length > 4 ? p[4].trim() : null)
                        .unitsPerPurchaseUnit(p.length > 5 ? parseDouble(p[5], 1) : 1.0)
                        .build();
                prod = productRepository.save(prod);
                measureUnitService.seedDefaultsIfEmpty(orgId);
                if (p.length > 5 && parseDouble(p[5], 1) > 1) {
                    productUomService.syncBoxUomFromLegacy(prod);
                }
                valid++;
            }
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
        auditService.log("PRODUCT_IMPORT", "Organization", orgId);
        return Dto.ProductImportPreviewDto.builder().validRows(valid).invalidRows(invalid).errors(errors).build();
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
