package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.SupplierManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierManagementService supplierManagementService;

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASES_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public List<Dto.SupplierDto> list(@AuthenticationPrincipal String userId) {
        return supplierManagementService.list(userId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASES_CREATE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Dto.SupplierDto> create(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody Dto.CreateSupplierRequest body) {
        return ResponseEntity.status(201).body(supplierManagementService.create(userId, body));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASES_UPDATE') or hasRole('ORG_MANAGER')")
    public Dto.SupplierDto update(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @RequestBody Dto.UpdateSupplierRequest body) {
        return supplierManagementService.update(userId, id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASES_DELETE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal String userId, @PathVariable String id) {
        supplierManagementService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
