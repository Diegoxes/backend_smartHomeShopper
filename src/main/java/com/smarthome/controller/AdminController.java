package com.smarthome.controller;

import com.smarthome.config.MaintenanceState;
import com.smarthome.dto.Dto;
import com.smarthome.service.AdminRbacService;
import com.smarthome.service.AdminUserService;
import com.smarthome.service.AdminOrgService;
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
    private final AdminOrgService adminOrgService;
    private final MaintenanceState maintenanceState;

    @GetMapping("/rbac")
    public Dto.RbacMatrixResponse getRbac() {
        return adminRbacService.getMatrix();
    }

    /** Lista todos los roles disponibles (para selects de formularios). */
    @GetMapping("/roles")
    public List<Dto.AdminRoleDto> listRoles() {
        return adminRbacService.listRoles();
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

    // ===== GESTIÓN DE ORGANIZACIONES =====

    /** Lista todas las organizaciones con el estado indicado (PENDING, ACTIVE, REJECTED). */
    @GetMapping("/organizations")
    public List<Dto.PendingOrgDto> listOrganizations(
            @RequestParam(defaultValue = "PENDING") String status) {
        return adminOrgService.listByStatus(status);
    }

    /** Aprobar o rechazar una solicitud de onboarding. */
    @PostMapping("/organizations/{orgId}/review")
    public ResponseEntity<Void> reviewOrganization(
            @PathVariable String orgId,
            @Valid @RequestBody Dto.OrgApprovalRequest req) {
        adminOrgService.review(orgId, req);
        return ResponseEntity.ok().build();
    }
}
