package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.PurchaseRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseRecordService purchaseRecordService;

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASES_CREATE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Dto.PurchaseRowDto> record(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody Dto.CreatePurchaseRequest body) {
        return ResponseEntity.status(201).body(purchaseRecordService.createManual(userId, body));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASES_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public Dto.PurchasesPageDto list(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return purchaseRecordService.listFiltered(userId, productId, from, to);
    }
}
