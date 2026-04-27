package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<Dto.ProductResponse> list(@AuthenticationPrincipal String userId) {
        return productService.getAllByUser(userId);
    }

    @GetMapping("/{id}")
    public Dto.ProductResponse get(@PathVariable String id, @AuthenticationPrincipal String userId) {
        return productService.getById(id, userId);
    }

    @PostMapping
    public ResponseEntity<Dto.ProductResponse> create(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody Dto.CreateProductRequest req) {
        return ResponseEntity.status(201).body(productService.create(userId, req));
    }

    @PatchMapping("/{id}")
    public Dto.ProductResponse update(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @RequestBody Dto.UpdateProductRequest req) {
        return productService.update(id, userId, req);
    }

    @PostMapping("/{id}/consume")
    public Dto.ProductResponse consume(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @RequestBody Dto.ConsumeRequest req) {
        return productService.consume(id, userId, req);
    }

    @PostMapping("/{id}/restock")
    public Dto.ProductResponse restock(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @RequestBody Dto.ConsumeRequest req) {
        return productService.restock(id, userId, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal String userId) {
        productService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
