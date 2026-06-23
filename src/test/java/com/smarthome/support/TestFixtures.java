package com.smarthome.support;

import com.smarthome.dto.Dto;
import com.smarthome.entity.*;
import com.smarthome.security.SessionPrincipal;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class TestFixtures {

    public static final String USER_ID = "user-1";
    public static final String ORG_ID = "org-1";
    public static final String PRODUCT_ID = "prod-1";
    public static final String SKU = "SKU-001";

    private TestFixtures() {}

    public static Organization organization() {
        return Organization.builder()
                .id(ORG_ID)
                .name("Test Org")
                .industry("ferreteria")
                .status(Organization.Status.ACTIVE)
                .build();
    }

    public static User user() {
        return User.builder()
                .id(USER_ID)
                .email("test@example.com")
                .password("hashed")
                .name("Test User")
                .build();
    }

    public static Product product() {
        return Product.builder()
                .id(PRODUCT_ID)
                .organization(organization())
                .user(user())
                .sku(SKU)
                .name("Producto Test")
                .quantity(10.0)
                .minQuantity(5.0)
                .unit(Product.UnitType.UNIT)
                .consumptionPerUse(1.0)
                .unitsPerPurchaseUnit(1.0)
                .purchaseUnit(Product.UnitType.UNIT)
                .build();
    }

    public static Dto.CreateProductRequest createProductRequest() {
        Dto.CreateProductRequest req = new Dto.CreateProductRequest();
        req.setName("Producto Test");
        req.setSku(SKU);
        req.setQuantity(10.0);
        req.setMinQuantity(5.0);
        req.setUnit(Product.UnitType.UNIT);
        return req;
    }

    public static Dto.RegisterRequest registerRequest() {
        Dto.RegisterRequest req = new Dto.RegisterRequest();
        req.setEmail("new@example.com");
        req.setPassword("secret123");
        req.setName("New User");
        return req;
    }

    public static Dto.LoginRequest loginRequest() {
        Dto.LoginRequest req = new Dto.LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("secret123");
        return req;
    }

    public static SessionPrincipal orgMemberSession() {
        return new SessionPrincipal(USER_ID, ORG_ID, "MANAGER", null);
    }

    public static OrganizationSettings orgSettings() {
        return OrganizationSettings.builder()
                .organization(organization())
                .expiryAlertDays(7)
                .predictionHorizonDays(30)
                .build();
    }

    public static Category category() {
        return Category.builder()
                .id("cat-1")
                .organization(organization())
                .name("General")
                .colorHex("#6B7280")
                .build();
    }

    public static Role orgRole(String name) {
        return Role.builder().id(1L).name(name).build();
    }

    public static RoleModule roleModule(AppModule module, boolean read, boolean create) {
        RoleModule rm = new RoleModule();
        rm.setModule(module);
        rm.setCanRead(read);
        rm.setCanCreate(create);
        return rm;
    }

    public static AppModule inventoryModule() {
        return AppModule.builder().id(1L).key("INVENTORY").name("Inventario").build();
    }

    public static Dto.ConsumeRequest consumeRequest(double amount) {
        Dto.ConsumeRequest req = new Dto.ConsumeRequest();
        req.setAmount(amount);
        req.setNote("test");
        return req;
    }

    public static Product lowStockProduct() {
        Product p = product();
        p.setQuantity(3.0);
        p.setMinQuantity(5.0);
        return p;
    }

    public static Product expiringProduct() {
        Product p = product();
        p.setExpiryDate(LocalDate.now().plusDays(3));
        return p;
    }

    public static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
