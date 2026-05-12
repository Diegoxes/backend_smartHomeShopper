package com.smarthome.dto;

import com.smarthome.entity.Product;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Dto {

    @Data public static class RegisterRequest {
        @NotBlank @Email private String email;
        @NotBlank @Size(min = 6) private String password;
        @NotBlank private String name;
        private String whatsappNumber;
    }

    @Data public static class LoginRequest {
        @NotBlank @Email private String email;
        @NotBlank private String password;
    }

    @Data @Builder public static class AuthResponse {
        private String token;
        private String userId;
        private String name;
        private String email;
        /** Uno de: OWNER, MANAGER, MEMBER, VIEWER */
        private String role;
        /** Permisos por módulo (clave = key del módulo, p. ej. INVENTORY). */
        private java.util.List<ModulePermissionDto> permissions;
    }

    @Data @Builder
    public static class ModulePermissionDto {
        private String key;
        private boolean canCreate;
        private boolean canRead;
        private boolean canUpdate;
        private boolean canDelete;
    }

    @Data @Builder
    public static class AuthMeResponse {
        private String userId;
        private String name;
        private String email;
        private String role;
        private java.util.List<ModulePermissionDto> permissions;
    }

    @Data
    public static class MaintenanceToggleRequest {
        private boolean enabled;
    }

    @Data public static class CreateProductRequest {
        @NotBlank private String name;
        @NotNull @Min(0) private Double quantity;
        @NotNull @Min(0) private Double minQuantity;
        @NotNull private Product.UnitType unit;
        private Double consumptionPerUse;
        private LocalDate expiryDate;
        private String barcode;
        private String category;
        private String imageUrl;
    }

    @Data public static class UpdateProductRequest {
        private String name;
        private Double quantity;
        private Double minQuantity;
        private Product.UnitType unit;
        private Double consumptionPerUse;
        private LocalDate expiryDate;
        private String category;
    }

    @Data @Builder public static class ProductResponse {
        private String id;
        private String name;
        private Double quantity;
        private Double minQuantity;
        private String unit;
        private Double consumptionPerUse;
        private LocalDate expiryDate;
        private String barcode;
        private String category;
        private String imageUrl;
        private boolean lowStock;
        private boolean expiringSoon;
        private Double daysUntilEmpty;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data public static class ConsumeRequest {
        @NotNull @Positive private Double amount;
        private String note;
    }

    @Data public static class WhatsAppWebhook {
        private String from;
        private String body;
        private String profileName;
    }

    @Data @Builder public static class DashboardResponse {
        private long totalProducts;
        private long lowStockCount;
        private long expiringCount;
        private java.util.List<ProductResponse> lowStockProducts;
        private java.util.List<ProductResponse> expiringProducts;
        private java.util.List<ProductResponse> allProducts;
    }

    // ── Admin (solo OWNER) ───────────────────────────────────────────────────

    @Data @Builder
    public static class RbacMatrixResponse {
        private java.util.List<AdminRoleDto> roles;
        private java.util.List<AdminModuleDto> modules;
        private java.util.List<RoleModuleCellDto> permissions;
    }

    @Data @Builder
    public static class AdminRoleDto {
        private Long id;
        private String name;
    }

    @Data @Builder
    public static class AdminModuleDto {
        private Long id;
        private String name;
        private String key;
    }

    @Data @Builder
    public static class RoleModuleCellDto {
        private Long roleId;
        private Long moduleId;
        private boolean canCreate;
        private boolean canRead;
        private boolean canUpdate;
        private boolean canDelete;
    }

    @Data
    public static class RbacBatchUpdateRequest {
        @NotEmpty private java.util.List<RoleModuleCellDto> cells;
    }

    @Data @Builder
    public static class AdminUserRowDto {
        private String id;
        private String email;
        private String name;
        private String whatsappNumber;
        private Long roleId;
        private String roleName;
    }

    @Data
    public static class AdminCreateUserRequest {
        @NotBlank @Email private String email;
        @NotBlank @Size(min = 6) private String password;
        @NotBlank private String name;
        @NotNull private Long roleId;
        private String whatsappNumber;
    }

    @Data
    public static class AdminUpdateUserRoleRequest {
        @NotNull private Long roleId;
    }
}
