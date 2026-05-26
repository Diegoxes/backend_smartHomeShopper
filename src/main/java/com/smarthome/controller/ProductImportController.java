package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.ImageUploadService;
import com.smarthome.service.ProductImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductImportController {

    private final ProductImportService productImportService;
    private final ImageUploadService imageUploadService;

    @PostMapping("/import/preview")
    @PreAuthorize("hasAuthority('INVENTORY_CREATE')")
    public Dto.ProductImportPreviewDto preview(@RequestParam("file") MultipartFile file) {
        return productImportService.preview(file);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('INVENTORY_CREATE')")
    public Dto.ProductImportPreviewDto commit(@RequestParam("file") MultipartFile file) {
        return productImportService.commit(file);
    }

    @PostMapping("/images/presign")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    public ResponseEntity<Dto.ImageUploadResponse> presign(@Valid @RequestBody Dto.ImageUploadRequest req) {
        return ResponseEntity.ok(imageUploadService.presign(req));
    }
}
