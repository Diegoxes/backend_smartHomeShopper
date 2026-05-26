package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.entity.Category;
import com.smarthome.service.CategoryService;
import com.smarthome.service.UserPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final UserPermissionService userPermissionService;

    @GetMapping
    public ResponseEntity<List<Dto.CategoryResponse>> getAll(Authentication auth) {
        String userId = auth.getName();
        String orgId = userPermissionService.requireOrgId(userId);

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
    public ResponseEntity<Dto.CategoryResponse> create(
            @Valid @RequestBody Dto.CreateCategoryRequest req,
            Authentication auth
    ) {
        String userId = auth.getName();
        String orgId = userPermissionService.requireOrgId(userId);
        userPermissionService.checkPermission(userId, "INVENTORY", "CREATE");

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
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) {
        String userId = auth.getName();
        String orgId = userPermissionService.requireOrgId(userId);
        userPermissionService.checkPermission(userId, "INVENTORY", "DELETE");

        categoryService.delete(id, orgId);
        return ResponseEntity.noContent().build();
    }
}
