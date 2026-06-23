package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Organization;
import com.smarthome.entity.Product;
import com.smarthome.entity.StockLevel;
import com.smarthome.entity.Warehouse;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.StockLevelRepository;
import com.smarthome.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductRepository productRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationContextService orgContext;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Dto.WarehouseDto> list() {
        return warehouseRepository.findByOrganizationIdOrderByNameAsc(orgContext.requireActiveOrgId()).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public Dto.WarehouseDto create(Dto.CreateWarehouseRequest req) {
        String orgId = orgContext.requireActiveOrgId();
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .organization(org)
                .name(req.getName().trim())
                .isDefault(false)
                .build());
        auditService.log("WAREHOUSE_CREATE", "Warehouse", wh.getId());
        return toDto(wh);
    }

    @Transactional
    public void transfer(Dto.TransferStockRequest req) {
        String orgId = orgContext.requireActiveOrgId();
        Warehouse from = warehouseRepository.findByIdAndOrganizationId(req.getFromWarehouseId(), orgId)
                .orElseThrow(() -> new RuntimeException("Almacén origen no encontrado"));
        Warehouse to = warehouseRepository.findByIdAndOrganizationId(req.getToWarehouseId(), orgId)
                .orElseThrow(() -> new RuntimeException("Almacén destino no encontrado"));
        Product product = productRepository.findByIdAndOrganizationId(req.getProductId(), orgId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        StockLevel fromSl = stockLevelRepository.findByProductIdAndWarehouseId(product.getId(), from.getId())
                .orElseThrow(() -> new RuntimeException("Sin stock en origen"));
        if (fromSl.getQuantity() < req.getQuantity()) throw new RuntimeException("Stock insuficiente");

        fromSl.setQuantity(fromSl.getQuantity() - req.getQuantity());
        stockLevelRepository.save(fromSl);

        StockLevel toSl = stockLevelRepository.findByProductIdAndWarehouseId(product.getId(), to.getId())
                .orElse(StockLevel.builder().product(product).warehouse(to).quantity(0.0).build());
        toSl.setQuantity(toSl.getQuantity() + req.getQuantity());
        stockLevelRepository.save(toSl);
        auditService.log("WAREHOUSE_TRANSFER", "Product", product.getId());
    }

    private Dto.WarehouseDto toDto(Warehouse w) {
        return Dto.WarehouseDto.builder()
                .id(w.getId())
                .name(w.getName())
                .isDefault(w.isDefault())
                .build();
    }
}
