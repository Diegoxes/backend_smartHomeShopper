package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public List<Dto.WarehouseDto> list() {
        return warehouseService.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_CREATE')")
    public ResponseEntity<Dto.WarehouseDto> create(@Valid @RequestBody Dto.CreateWarehouseRequest req) {
        return ResponseEntity.status(201).body(warehouseService.create(req));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    public ResponseEntity<Void> transfer(@Valid @RequestBody Dto.TransferStockRequest req) {
        warehouseService.transfer(req);
        return ResponseEntity.ok().build();
    }
}
