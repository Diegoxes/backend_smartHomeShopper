package com.smarthome.controller;

import com.smarthome.config.MaintenanceState;
import com.smarthome.dto.Dto;
import com.smarthome.service.AdminRbacService;
import com.smarthome.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_OWNER')")
public class AdminController {

    private final AdminRbacService adminRbacService;
    private final AdminUserService adminUserService;
    private final MaintenanceState maintenanceState;

    @GetMapping("/rbac")
    public Dto.RbacMatrixResponse getRbac() {
        return adminRbacService.getMatrix();
    }

    @PutMapping("/rbac/permissions")
    public ResponseEntity<Void> updateRbacPermissions(@Valid @RequestBody Dto.RbacBatchUpdateRequest body) {
        adminRbacService.updatePermissionsBatch(body.getCells());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users")
    public List<Dto.AdminUserRowDto> listUsers() {
        return adminUserService.listUsers();
    }

    @PostMapping("/users")
    public ResponseEntity<Dto.AdminUserRowDto> createUser(@Valid @RequestBody Dto.AdminCreateUserRequest req) {
        return ResponseEntity.ok(adminUserService.createUser(req));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable String id,
            @Valid @RequestBody Dto.AdminUpdateUserRoleRequest req) {
        adminUserService.updateUserRole(id, req);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/maintenance")
    public Map<String, Boolean> getMaintenance() {
        return Map.of("enabled", maintenanceState.isEnabled());
    }

    @PutMapping("/maintenance")
    public ResponseEntity<Void> setMaintenance(@Valid @RequestBody Dto.MaintenanceToggleRequest body) {
        maintenanceState.setEnabled(body.isEnabled());
        return ResponseEntity.ok().build();
    }
}
