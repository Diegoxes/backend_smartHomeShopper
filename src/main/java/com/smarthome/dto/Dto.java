package com.smarthome.dto;

import com.smarthome.entity.Product;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Dto {

    @Data public static class RegisterRequest {
        @NotBlank @Email @Size(max = 254) private String email;
        @NotBlank @Size(min = 6, max = 128) private String password;
        @NotBlank @Size(min = 2, max = 100) private String name;
        @Size(max = 20) @Pattern(regexp = "^$|^\\+?[0-9]{7,19}$", message = "WhatsApp inválido") private String whatsappNumber;
    }

    @Data public static class LoginRequest {
        @NotBlank @Email @Size(max = 254) private String email;
        @NotBlank @Size(max = 128) private String password;
    }

    @Data @Builder public static class AuthResponse {
        private String token;
        private String userId;
        private String name;
        private String email;
        /** Rol mostrado: PLATFORM_OWNER, MANAGER, MEMBER, VIEWER o PENDING */
        private String role;
        private String platformRole;
        private String orgRole;
        private String orgId;
        /** PENDING = esperando aprobación, ACTIVE = aprobada, REJECTED = rechazada */
        private String orgStatus;
        private boolean needsOnboarding;
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
        private String platformRole;
        private String orgRole;
        private String orgId;
        private String orgStatus;
        private boolean needsOnboarding;
        private java.util.List<ModulePermissionDto> permissions;
    }

    /** Solicitud de onboarding pendiente de aprobación (vista del PLATFORM_OWNER) */
    @Data @Builder
    public static class PendingOrgDto {
        private String orgId;
        private String orgName;
        private String industry;
        private String country;
        private String orgStatus;
        private LocalDateTime createdAt;
        private String managerUserId;
        private String managerName;
        private String managerEmail;
    }

    @Data
    public static class OrgApprovalRequest {
        @NotBlank private String action; // "APPROVE" o "REJECT"
        private String reason;
    }

    @Data
    public static class MaintenanceToggleRequest {
        private boolean enabled;
    }

    @Data public static class CreateProductRequest {
        @NotBlank @Size(max = 255) private String name;
        @NotBlank @Size(max = 64) private String sku;
        private String internalCode;
        @NotNull @Min(0) private Double quantity;
        @NotNull @Min(0) private Double minQuantity;
        @NotNull private Product.UnitType unit;
        private Double consumptionPerUse;
        private LocalDate expiryDate;
        @Size(max = 100) private String barcode;
        private String category;
        private String imageUrl;
        private java.math.BigDecimal unitCost;
        private java.math.BigDecimal salePrice;
        private Product.UnitType purchaseUnit;
        private Double unitsPerPurchaseUnit;
        /** Opcional: proveedor del stock inicial (si hay unitCost se registra compra). */
        private String supplierId;
        private java.util.List<ProductUomInput> productUoms;
    }

    @Data public static class UpdateProductRequest {
        private String name;
        private String sku;
        private String internalCode;
        private Double quantity;
        private Double minQuantity;
        private Product.UnitType unit;
        private Double consumptionPerUse;
        private LocalDate expiryDate;
        private String category;
        private java.math.BigDecimal unitCost;
        private java.math.BigDecimal salePrice;
        private Product.UnitType purchaseUnit;
        private Double unitsPerPurchaseUnit;
        /** @deprecated usar PUT /products/{id}/uoms */
        private java.util.List<ProductUomInput> productUoms;
    }

    @Data @Builder public static class ProductResponse {
        private String id;
        private String sku;
        private String internalCode;
        private String name;
        private Double quantity;
        private Double minQuantity;
        private String unit;
        private Double consumptionPerUse;
        private LocalDate expiryDate;
        private String barcode;
        private String category;
        private String imageUrl;
        private java.math.BigDecimal salePrice;
        private java.math.BigDecimal lastCost;
        private java.math.BigDecimal avgCost;
        private java.math.BigDecimal marginPercent;
        private String purchaseUnit;
        private Double unitsPerPurchaseUnit;
        /** @deprecated mantener lectura; usar productUoms */
        private java.util.List<ProductUomDto> productUoms;
        private java.util.List<StockBreakdownDto> stockBreakdown;
        private String stockDisplay;
        private boolean lowStock;
        private boolean expiringSoon;
        private Double daysUntilEmpty;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data public static class ConsumeRequest {
        @NotNull @Positive private Double amount;
        @Size(max = 500) private String note;
        /** Con restock registra también una compra. */
        private String supplierId;
        /** Costo por unidad base confirmado. */
        private java.math.BigDecimal unitPrice;
        /** Unidad en que se expresa amount (null = legacy / unidad base en consumo). */
        private String measureUnitId;
        /** Precio por paquete/caja ingresado (solo referencia UI). */
        private java.math.BigDecimal packagePrice;
        /** PER_BASE | PER_PACKAGE */
        private String costInputMode;
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

    @Data public static class AdjustStockRequest {
        @NotNull private Double delta;
        @NotBlank @Size(max = 500) private String reason;
    }

    @Data @Builder public static class ProductMovementDto {
        private LocalDateTime at;
        private String actionType;
        private Double quantityChange;
        private String source;
        private String note;
        private String purchaseId;
    }

    // ── Organización ──────────────────────────────────────────────────────────

    @Data public static class OnboardingRequest {
        @NotBlank @Size(min = 2, max = 255) private String name;
        @Size(max = 128) private String industry;
        private String currency;
        private String country;
        private String timezone;
    }

    @Data public static class UpdateOrganizationRequest {
        private String name;
        private String industry;
        private String currency;
        private String country;
        private String timezone;
        private Integer expiryAlertDays;
        private Integer predictionHorizonDays;
    }

    @Data @Builder public static class OrganizationDto {
        private String id;
        private String name;
        private String industry;
        private String currency;
        private String country;
        private String timezone;
        private Integer maxMembers;
        private Integer expiryAlertDays;
        private Integer predictionHorizonDays;
    }

    @Data @Builder public static class OrgMemberDto {
        private String id;
        private String userId;
        private String email;
        private String name;
        private String orgRole;
        private String whatsappNumber;
    }

    @Data public static class CreateOrgMemberRequest {
        @NotBlank @Email @Size(max = 254) private String email;
        @NotBlank @Size(min = 6, max = 128) private String password;
        @NotBlank @Size(min = 2, max = 100) private String name;
        @NotBlank private String orgRole;
        @Size(max = 20) @Pattern(regexp = "^$|^\\+?[0-9]{7,19}$", message = "WhatsApp inválido") private String whatsappNumber;
    }

    @Data public static class UpdateOrgMemberRequest {
        private String name;
        private String orgRole;
    }

    @Data @Builder public static class PlatformOrganizationRowDto {
        private String id;
        private String name;
        private String industry;
        private int memberCount;
        private int maxMembers;
        private LocalDateTime createdAt;
    }

    @Data @Builder public static class PlatformUserRowDto {
        private String id;
        private String email;
        private String name;
        private String orgName;
        private String orgRole;
        private String platformRole;
    }

    @Data public static class MaxMembersRequest {
        @NotNull @Min(1) private Integer maxMembers;
    }

    // ── Dashboard ejecutivo ───────────────────────────────────────────────────

    @Data @Builder public static class ExecutiveDashboardDto {
        private java.math.BigDecimal totalStockValue;
        private java.math.BigDecimal monthPurchaseSpend;
        private long lowStockCount;
        private long expiringCount;
        private java.util.List<RotationReportRowDto> topRotation;
        private java.util.List<String> stagnantProductIds;
    }

    // ── Almacenes ─────────────────────────────────────────────────────────────

    @Data @Builder public static class WarehouseDto {
        private String id;
        private String name;
        private boolean isDefault;
    }

    @Data public static class CreateWarehouseRequest {
        @NotBlank private String name;
    }

    @Data public static class TransferStockRequest {
        @NotBlank private String productId;
        @NotBlank private String fromWarehouseId;
        @NotBlank private String toWarehouseId;
        @NotNull @Positive private Double quantity;
    }

    // ── Import / imágenes ─────────────────────────────────────────────────────

    @Data @Builder public static class ProductImportPreviewDto {
        private int validRows;
        private int invalidRows;
        private java.util.List<String> errors;
    }

    @Data public static class ImageUploadRequest {
        @NotBlank private String filename;
        @NotBlank private String contentType;
    }

    @Data @Builder public static class ImageUploadResponse {
        private String uploadUrl;
        private String publicUrl;
    }

    // ── Reportes adicionales ────────────────────────────────────────────────────

    @Data @Builder public static class SupplierSpendRowDto {
        private String supplierId;
        private String supplierName;
        private java.math.BigDecimal totalSpend;
    }

    @Data @Builder public static class ChannelReportRowDto {
        private String channel;
        private double unitsConsumed;
    }

    @Data @Builder public static class InventorySnapshotDto {
        private java.time.LocalDate date;
        private java.math.BigDecimal totalValue;
        private String breakdownJson;
    }

    @Data public static class PasswordResetRequest {
        @NotBlank @Email private String email;
    }

    @Data @Builder public static class PasswordResetResponse {
        private String message;
    }

    // ── Admin (solo PLATFORM_OWNER) ───────────────────────────────────────────

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
        /** Id del rol en tabla roles (plantilla RBAC) — refleja rol plataforma u org */
        private Long roleId;
        private String roleName;
        /** Rol de plataforma en users.role_id (solo PLATFORM_OWNER) */
        private String platformRole;
        /** Rol dentro de la organización */
        private String orgRole;
        private String organizationId;
        private String organizationName;
    }

    @Data
    public static class AdminCreateUserRequest {
        @NotBlank @Email @Size(max = 254) private String email;
        @NotBlank @Size(min = 6, max = 128) private String password;
        @NotBlank @Size(min = 2, max = 100) private String name;
        @NotNull private Long roleId;
        @Size(max = 20) @Pattern(regexp = "^$|^\\+?[0-9]{7,19}$", message = "WhatsApp inválido") private String whatsappNumber;
        /** Obligatorio si roleId es MANAGER, MEMBER o VIEWER */
        private String organizationId;
    }

    @Data
    public static class AdminUpdateUserRoleRequest {
        @NotNull private Long roleId;
        /** Obligatorio al asignar rol org si el usuario aún no tiene organización */
        private String organizationId;
    }

    // ── Alias de productos ────────────────────────────────────────────────────

    @Data
    public static class AddAliasRequest {
        @NotBlank @Size(min = 2, max = 255) private String alias;
    }

    @Data @Builder
    public static class ProductAliasDto {
        private String id;
        private String aliasText;
        private String normalizedAlias;
        private boolean learnedWhatsApp;
    }

    // ── Compras y proveedores ───────────────────────────────────────────────────

    @Data
    public static class CreatePurchaseRequest {
        @NotBlank private String productId;
        @NotNull @Positive private Double quantity;
        private String supplierId;
        private java.math.BigDecimal unitPrice;
        private String currency = "MXN";
        private LocalDateTime purchasedAt;
        private String note;
    }

    @Data @Builder
    public static class PurchaseRowDto {
        private String id;
        private String productId;
        private String productName;
        private String supplierId;
        private String supplierName;
        private Double quantity;
        private java.math.BigDecimal unitPrice;
        private java.math.BigDecimal totalAmount;
        private String currency;
        private LocalDateTime purchasedAt;
        private String source;
    }

    @Data @Builder
    public static class PurchasesPageDto {
        private java.util.List<PurchaseRowDto> items;
        private java.math.BigDecimal periodTotalSpend;
    }

    @Data
    public static class CreateSupplierRequest {
        @NotBlank @Size(max = 255) private String name;
        private String phone;
        @Min(0) private Integer leadTimeDays;
        private String notes;
    }

    @Data
    public static class UpdateSupplierRequest {
        private String name;
        private String phone;
        @Min(0) private Integer leadTimeDays;
        private String notes;
    }

    @Data @Builder
    public static class SupplierDto {
        private String id;
        private String name;
        private String phone;
        private Integer leadTimeDays;
        private String notes;
    }

    // ── Informes ───────────────────────────────────────────────────────────────

    @Data @Builder
    public static class RotationReportRowDto {
        private String productId;
        private String productName;
        private String category;
        private double unitsConsumed;
        private Double avgDailyConsumption;
        private Double estimatedDaysRemaining;
        private String velocity; // FAST / NORMAL / SLOW / UNKNOWN
    }

    @Data @Builder
    public static class RotationReportDto {
        private java.time.LocalDate fromInclusive;
        private java.time.LocalDate toInclusive;
        private java.util.List<RotationReportRowDto> rows;
    }

    @Data @Builder
    public static class CategoryBreakdownDto {
        private String category;
        private long skuCount;
        private double quantitySum;
        private java.math.BigDecimal estimatedSpend;
    }

    @Data @Builder
    public static class InventoryReportDto {
        private long totalSku;
        private java.math.BigDecimal totalEstimatedValue;
        private java.util.List<Dto.CategoryBreakdownDto> byCategory;
        private java.util.List<RotationReportRowDto> topConsumed30d;
        private java.util.List<String> stagnantProductIds;

    }

    @Getter
    @AllArgsConstructor
    public enum ReportExportFormat { XLSX, PDF }

    /** Placeholder hasta integrar almacén S3/signing. */
    @Data @Builder
    public static class ReportExportQueuedDto {
        private String format;
        private String message;
    }

    // ── Category ──────────────────────────────────────────────────────────────────
    @Data
    public static class CreateCategoryRequest {
        @NotBlank @Size(max = 100) private String name;
        private String description;
        private String colorHex;
    }

    @Data @Builder
    public static class CategoryResponse {
        private String id;
        private String name;
        private String description;
        private String colorHex;
        private String createdAt;
    }

    // ── Unidades de medida ────────────────────────────────────────────────────────

    @Data @Builder
    public static class MeasureUnitDto {
        private String id;
        private String code;
        private String name;
        private boolean baseUnit;
        private boolean active;
    }

    @Data
    public static class CreateMeasureUnitRequest {
        @NotBlank @Size(max = 32) private String code;
        @NotBlank @Size(max = 100) private String name;
        private boolean baseUnit;
    }

    @Data
    public static class UpdateMeasureUnitRequest {
        private String name;
        private Boolean active;
    }

    @Data @Builder
    public static class ProductUomDto {
        private String id;
        private String measureUnitId;
        private String code;
        private String name;
        private Double factorToBase;
    }

    @Data
    public static class ProductUomInput {
        @NotBlank private String measureUnitId;
        @NotNull @Positive private Double factorToBase;
    }

    @Data
    public static class ReplaceProductUomsRequest {
        private java.util.List<ProductUomInput> items;
    }

    @Data @Builder
    public static class StockBreakdownDto {
        private String measureUnitId;
        private String code;
        private String name;
        private Double factor;
        private int fullUnits;
        private double remainder;
    }
}

