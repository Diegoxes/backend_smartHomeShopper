package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.ProductAliasService;
import com.smarthome.service.ProductService;
import com.smarthome.service.ProductUomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductAliasService productAliasService;
    private final ProductUomService productUomService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ') or hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public List<Dto.ProductResponse> list(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(required = false) Boolean expiringSoon,
            @RequestParam(required = false) Integer stagnantDays,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        return productService.listFiltered(lowStock, expiringSoon, stagnantDays, category, q);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public Dto.ProductResponse get(@PathVariable String id, @AuthenticationPrincipal String userId) {
        return productService.getById(id, userId);
    }

    @GetMapping("/{id}/movements")
    @PreAuthorize("hasAuthority('INVENTORY_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public List<Dto.ProductMovementDto> movements(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return productService.movements(id, from, to);
    }

    @PostMapping //crea unu 
    @PreAuthorize("hasAuthority('INVENTORY_CREATE') or hasAnyRole('ORG_MANAGER','ORG_MEMBER')")
    public ResponseEntity<Dto.ProductResponse> create(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody Dto.CreateProductRequest req) {
        return ResponseEntity.status(201).body(productService.create(userId, req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE') or hasAnyRole('ORG_MANAGER','ORG_MEMBER')")
    public Dto.ProductResponse update(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @RequestBody Dto.UpdateProductRequest req) {
        return productService.update(id, userId, req);
    }

    @PostMapping("/{id}/adjust")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE') or hasAnyRole('ORG_MANAGER','ORG_MEMBER')")
    public Dto.ProductResponse adjust(
            @PathVariable String id,
            @Valid @RequestBody Dto.AdjustStockRequest req) {
        return productService.adjust(id, req);
    }

    @PostMapping("/{id}/aliases")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    public ResponseEntity<Dto.ProductAliasDto> addAlias(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody Dto.AddAliasRequest body) {
        return ResponseEntity.status(201).body(productAliasService.addManual(id, userId, body.getAlias()));
    }

    @PostMapping("/{id}/consume")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE') or hasAnyRole('ORG_MANAGER','ORG_MEMBER')")
    public Dto.ProductResponse consume(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @RequestBody Dto.ConsumeRequest req) {
        return productService.consume(id, userId, req);
    }

    @PostMapping("/{id}/restock")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE') or hasAnyRole('ORG_MANAGER','ORG_MEMBER')")
    public Dto.ProductResponse restock(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @RequestBody Dto.ConsumeRequest req) {
        return productService.restock(id, userId, req);
    }

    @GetMapping("/{id}/uoms")
    @PreAuthorize("hasAuthority('INVENTORY_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public List<Dto.ProductUomDto> listUoms(@PathVariable String id) {
        return productUomService.listForProduct(id);
    }

    @PutMapping("/{id}/uoms")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE') or hasAnyRole('ORG_MANAGER','ORG_MEMBER')")
    public List<Dto.ProductUomDto> replaceUoms(
            @PathVariable String id,
            @RequestBody Dto.ReplaceProductUomsRequest body) {
        return productUomService.replaceForProduct(id, body != null ? body.getItems() : List.of());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_DELETE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal String userId) {
        productService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
