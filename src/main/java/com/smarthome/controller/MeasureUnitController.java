package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.MeasureUnitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/measure-units")
@RequiredArgsConstructor
public class MeasureUnitController {

    private final MeasureUnitService measureUnitService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public List<Dto.MeasureUnitDto> list() {
        return measureUnitService.listActive();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Dto.MeasureUnitDto> create(
            @Valid @RequestBody Dto.CreateMeasureUnitRequest req) {
        return ResponseEntity.status(201).body(measureUnitService.create(req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE') or hasRole('ORG_MANAGER')")
    public Dto.MeasureUnitDto update(
            @PathVariable String id,
            @RequestBody Dto.UpdateMeasureUnitRequest req) {
        return measureUnitService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        measureUnitService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
