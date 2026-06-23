package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Category;
import com.smarthome.service.CategoryService;
import com.smarthome.service.OrganizationContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final OrganizationContextService orgContext;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public ResponseEntity<List<Dto.CategoryResponse>> getAll(@AuthenticationPrincipal String userId) {
        String orgId = orgContext.requireActiveOrgId();

        List<Category> categories = categoryService.getAllByOrganization(orgId);

        List<Dto.CategoryResponse> response = categories.stream()
                .map(c -> Dto.CategoryResponse.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .colorHex(c.getColorHex())
                        .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_CREATE') or hasAnyRole('ORG_MANAGER','ORG_MEMBER')")
    public ResponseEntity<Dto.CategoryResponse> create(
            @Valid @RequestBody Dto.CreateCategoryRequest req,
            @AuthenticationPrincipal String userId
    ) {
        String orgId = orgContext.requireActiveOrgId();

        Category category = categoryService.create(
                orgId,
                req.getName(),
                req.getDescription(),
                req.getColorHex()
        );

        Dto.CategoryResponse response = Dto.CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .colorHex(category.getColorHex())
                .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toString() : null)
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_DELETE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal String userId) {
        String orgId = orgContext.requireActiveOrgId();
        categoryService.delete(id, orgId);
        return ResponseEntity.noContent().build();
    }
}
