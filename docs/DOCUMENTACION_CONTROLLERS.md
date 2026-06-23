# Documentación línea por línea — Controllers Java (Backend)

> **Proyecto:** SmartInventory / smartHomeShopper  
> **Ubicación:** `backend_smartHomeShopper/src/main/java/com/smarthome/controller/`  
> **Stack:** Spring Boot + Spring Security + Lombok  
> **Guía funcional (puede / no puede):** [FUNCIONALIDADES_APLICACION.md](FUNCIONALIDADES_APLICACION.md)

---

## Índice

0. [Teoría: qué es un Controller en Spring](#0-teoría-qué-es-un-controller-en-spring)
1. [AuthController](#1-authcontroller)
2. [OrganizationController](#2-organizationcontroller)
3. [ProductController](#3-productcontroller)
4. [DashboardController](#4-dashboardcontroller)
5. [PurchaseController](#5-purchasecontroller)
6. [SupplierController](#6-suppliercontroller)
7. [ReportInsightsController](#7-reportinsightscontroller)
8. [AdminController](#8-admincontroller)
9. [PlatformController](#9-platformcontroller)
10. [Mapa frontend ↔ backend](#10-mapa-frontend--backend)
11. [Resumen: Controller + Service + funcionalidad en la app](#11-resumen-controller--service--funcionalidad-en-la-app)

---

## 0. Teoría: qué es un Controller en Spring

Un **Controller** en Spring Boot es la capa que **recibe peticiones HTTP** del frontend (o WhatsApp, Postman, etc.) y **devuelve JSON** (o archivos). **No** contiene la lógica de negocio pesada: delega en **Services**.

```
Cliente HTTP  →  Controller  →  Service  →  Repository  →  Base de datos
   (React)        (rutas)      (negocio)    (JPA/SQL)
```

**Regla de oro:** el Controller **nunca** debería hacer SQL ni reglas complejas. Solo:
1. Recibe la petición HTTP
2. Comprueba permisos (`@PreAuthorize`)
3. Llama **un método del Service**
4. Devuelve JSON + código HTTP (200, 201, 204, 400, 403…)

El **Service** es donde ocurre la funcionalidad real:
- Valida reglas de negocio (SKU duplicado, stock insuficiente…)
- Obtiene la **org del tenant** (`OrganizationContextService`) — cada empresa solo ve sus datos
- Usa **Repositories** para leer/escribir en PostgreSQL
- Genera JWT, calcula KPIs, registra compras, etc.

```
Ejemplo ProductController:

  DELETE /products/abc123
       ↓
  ProductController.delete(id, userId)
       ↓
  ProductService.delete(id, userId)
       ↓  findOwned → solo si producto pertenece a la org del JWT
       ↓  productRepo.delete(...)
       ↓
  HTTP 204 No Content
```

### Anotaciones que verás en todos los controllers

| Anotación | Qué hace |
|-----------|----------|
| `@RestController` | Clase que expone API REST; cada método devuelve JSON (no vista HTML). |
| `@RequestMapping("/ruta")` | Prefijo base de todas las rutas de esa clase. |
| `@GetMapping` | Maneja HTTP GET (leer). |
| `@PostMapping` | HTTP POST (crear / acción). |
| `@PatchMapping` | HTTP PATCH (actualizar parcial). |
| `@PutMapping` | HTTP PUT (reemplazar / batch update). |
| `@DeleteMapping` | HTTP DELETE (borrar). |
| `@RequiredArgsConstructor` | Lombok: genera constructor con campos `final` (inyección de dependencias). |
| `@Valid @RequestBody` | Valida el JSON del body contra reglas del DTO. |
| `@PathVariable` | Parámetro de la URL: `/products/{id}` → `id`. |
| `@RequestParam` | Query string: `/products?q=leche` → `q`. |
| `@AuthenticationPrincipal String userId` | ID del usuario logueado extraído del JWT. |
| `@PreAuthorize("...")` | Spring Security: solo entra si cumple permiso/rol. |
| `ResponseEntity.ok(...)` | Respuesta HTTP 200 con cuerpo JSON. |
| `ResponseEntity.status(201)` | HTTP 201 Created (recurso nuevo). |
| `ResponseEntity.noContent()` | HTTP 204 (borrado OK, sin body). |

### Flujo de una petición típica

1. Frontend: `GET /api/products?q=leche` con header `Authorization: Bearer <JWT>`
2. Spring Security valida JWT y carga permisos
3. `@PreAuthorize` comprueba `INVENTORY_READ`
4. Controller recibe params y llama `productService.listFiltered(...)`
5. Service consulta DB filtrando por **org del tenant**
6. Controller devuelve `List<ProductResponse>` como JSON

---

## 1. AuthController  #######################

**Archivo:** `AuthController.java`  
**Ruta base:** `/auth`  
**Frontend:** `AuthPage.tsx` → `authService.login`, `register`, `maintenanceStatus`

### Qué hace en la aplicación

Es la **puerta de entrada** al sistema. Sin pasar por aquí no hay JWT ni acceso al resto de la app.

| Funcionalidad en la app | Endpoint | Cuándo se usa |
|-------------------------|----------|---------------|
| Registro de cuenta nueva | `POST /auth/register` | Tab "Registrarse" en AuthPage |
| Inicio de sesión | `POST /auth/login` | Tab "Iniciar sesión" |
| Saber si hay mantenimiento | `GET /auth/maintenance` | Banner ámbar antes del login |
| Refrescar perfil/permisos | `GET /auth/me` | AuthContext al cargar la app |
| Reset de contraseña | `POST /auth/password-reset` | Flujo recuperación (opcional en UI) |

### Controller → Service

```
AuthController                    Services que llama
─────────────────────────────────────────────────────────
register(req)          →         AuthService.register()
login(req)             →         AuthService.login()
me(userId)             →         AuthService.me()
maintenanceStatus()    →         MaintenanceState (bean config, no service)
passwordReset(req)     →         PasswordResetService.requestReset()
```

### Qué hace cada Service

| Service | Responsabilidad |
|---------|-----------------|
| **AuthService** | Normaliza email, hashea password con BCrypt, guarda usuario en DB, valida login, arma JWT con `JwtService`, incluye permisos RBAC vía `UserPermissionService`, devuelve `AuthResponse`. |
| **PasswordResetService** | Busca email (sin revelar si existe) y responde mensaje genérico de reset. |
| **MaintenanceState** | Bean en memoria con flag `enabled`; compartido con AdminController. |
| **JwtService** *(interno)* | Firma y valida tokens JWT. |
| **UserPermissionService** *(interno)* | Resuelve authorities (`INVENTORY_READ`, etc.) según rol y matriz RBAC. |

### Código completo con explicación

```java
package com.smarthome.controller;

import com.smarthome.config.MaintenanceState;
import com.smarthome.dto.Dto;
import com.smarthome.service.AuthService;
import com.smarthome.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
```

| Línea | Qué hace |
|-------|----------|
| `package` | Namespace Java del proyecto. |
| `MaintenanceState` | Bean en memoria: flag mantenimiento ON/OFF. |
| `Dto` | Clases request/response (RegisterRequest, AuthResponse, etc.). |
| `AuthService` | Lógica login, register, me. |
| `PasswordResetService` | Reset de contraseña. |
| `@Valid` | Activa validación Jakarta (email, min length, etc.). |
| `ResponseEntity` | Wrapper HTTP status + body. |
| `AccessDeniedException` | Error 403 si no autenticado. |
| `@AuthenticationPrincipal` | Usuario actual del JWT. |

```java
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MaintenanceState maintenanceState;
    private final PasswordResetService passwordResetService;
```

| Línea | Qué hace |
|-------|----------|
| `@RestController` | API REST en `/auth/*`. |
| `@RequestMapping("/auth")` | Todas las rutas empiezan con `/auth`. |
| `@RequiredArgsConstructor` | Inyecta los 3 services por constructor. |
| `private final` | Dependencias inmutables (DI de Spring). |

```java
    @PostMapping("/register")
    public ResponseEntity<Dto.AuthResponse> register(@Valid @RequestBody Dto.RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }
```

| Línea | Qué hace |
|-------|----------|
| `@PostMapping("/register")` | `POST /auth/register` |
| `@RequestBody` | JSON del body → objeto Java `RegisterRequest`. |
| `@Valid` | Si email inválido o password corto → 400 Bad Request. |
| `authService.register(req)` | Crea usuario, hashea password, genera JWT. |
| `ResponseEntity.ok(...)` | HTTP 200 + `{ token, name, permissions, ... }`. |

```java
    @PostMapping("/login")
    public ResponseEntity<Dto.AuthResponse> login(@Valid @RequestBody Dto.LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
```

| Línea | Qué hace |
|-------|----------|
| `POST /auth/login` | Email + password → JWT si credenciales OK. |
| Mismo patrón | Controller delgado; validación en service (usuario existe, password match). |

```java
    @GetMapping("/me")
    public ResponseEntity<Dto.AuthMeResponse> me(@AuthenticationPrincipal String userId) {
        if (userId == null) {
            throw new AccessDeniedException("No autenticado");
        }
        return ResponseEntity.ok(authService.me(userId));
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /auth/me` | Perfil del usuario logueado (requiere JWT). |
| `@AuthenticationPrincipal String userId` | Spring pone el `sub` del JWT (ID usuario). |
| `if (userId == null)` | Guard extra: token inválido o ausente. |
| `authService.me(userId)` | Devuelve nombre, rol, org, permisos actualizados. |

```java
    /** Público: bandera de mantenimiento para la pantalla de login (sin JWT). */
    @GetMapping("/maintenance")
    public Map<String, Boolean> maintenanceStatus() {
        return Map.of("enabled", maintenanceState.isEnabled());
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /auth/maintenance` | **Público** — AuthPage lo llama antes del login. |
| `maintenanceState.isEnabled()` | Lee flag global en memoria. |
| `Map.of("enabled", ...)` | JSON: `{ "enabled": true/false }`. |

```java
    @PostMapping("/password-reset")
    public ResponseEntity<Dto.PasswordResetResponse> passwordReset(@Valid @RequestBody Dto.PasswordResetRequest req) {
        return ResponseEntity.ok(passwordResetService.requestReset(req));
    }
}
```

| Línea | Qué hace |
|-------|----------|
| `POST /auth/password-reset` | Solicitud de reset (email). |
| Delega en `PasswordResetService` | Envía token/link o simula flujo. |

---

## 2. OrganizationController  #########################

**Archivo:** `OrganizationController.java`  
**Ruta base:** `/organizations`  
**Frontend:** `OnboardingPage`, `OrgTeamPage`

### Qué hace en la aplicación

Gestiona el **tenant** (empresa B2B): crear org post-registro, ver datos propios e **invitar miembros del equipo**.

| Funcionalidad en la app | Endpoint | Pantalla |
|-------------------------|----------|----------|
| Crear organización (onboarding) | `POST /organizations` | OnboardingPage |
| Ver mi org | `GET /organizations/me` | Configuración |
| Editar org | `PATCH /organizations/me` | Settings |
| Listar miembros | `GET /organizations/me/members` | OrgTeamPage tabla |
| Invitar miembro | `POST /organizations/me/members` | OrgTeamPage formulario |
| Cambiar rol miembro | `PATCH /organizations/me/members/{id}` | Admin org |
| Eliminar miembro | `DELETE /organizations/me/members/{id}` | OrgTeamPage Eliminar |

### Controller → Service

```
OrganizationController              OrganizationService
──────────────────────────────────────────────────────────
onboard(req)              →         onboard() — org + almacén + categorías + JWT nuevo
me()                      →         getMyOrganization()
updateMe(req)             →         updateMyOrganization()
members()                 →         listMembers()
addMember(req)            →         addMember()
updateMember(id, req)     →         updateMember()
removeMember(id)          →         removeMember()
```

### Qué hace OrganizationService

| Método | Lógica de negocio |
|--------|-------------------|
| **onboard** | Crea Organization ACTIVE, settings, member MANAGER, almacén "Principal", categorías seed; genera **nuevo JWT** con orgId. |
| **listMembers / addMember / removeMember** | CRUD miembros solo en la org del JWT; no borra MANAGER. |
| **OrganizationContextService** *(interno)* | Lee userId/orgId del JWT — **multi-tenant**. |

```java
@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController { 
    private final OrganizationService organizationService;
```

| Línea | Qué hace |
|-------|----------|
| Prefijo `/organizations` | Todas las rutas de org van aquí. |
| Un solo service | Toda la lógica en `OrganizationService`. |

```java
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Dto.AuthResponse> onboard(@Valid @RequestBody Dto.OnboardingRequest req) {
        return ResponseEntity.ok(organizationService.onboard(req));
    }
```

| Línea | Qué hace |
|-------|----------|
| `@PostMapping` (sin path) | `POST /organizations` |
| `@PreAuthorize("isAuthenticated()")` | Cualquier usuario con JWT válido. |
| `onboard(req)` | Crea org, asigna usuario como MANAGER, **nuevo JWT** con orgId. |
| Retorna `AuthResponse` | Frontend hace `login(res)` con token actualizado. |

```java
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Dto.OrganizationDto me() {
        return organizationService.getMyOrganization();
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /organizations/me` | Datos de la org del tenant actual. |
| Service infiere org | Desde contexto de seguridad / JWT. |

```java
    @PatchMapping("/me")
    @PreAuthorize("hasAuthority('USERS_UPDATE') or hasRole('ORG_MANAGER')")
    public Dto.OrganizationDto updateMe(@RequestBody Dto.UpdateOrganizationRequest req) {
        return organizationService.updateMyOrganization(req);
    }
```

| Línea | Qué hace |
|-------|----------|
| `PATCH /organizations/me` | Actualiza nombre, moneda, etc. |
| `@PreAuthorize` | Manager o permiso USERS_UPDATE. |

```java
    @GetMapping("/me/members")
    @PreAuthorize("hasAuthority('USERS_READ') or hasRole('ORG_MANAGER')")
    public List<Dto.OrgMemberDto> members() {
        return organizationService.listMembers();
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /organizations/me/members` | Lista equipo → **OrgTeamPage** `load()`. |

```java
    @PostMapping("/me/members")
    @PreAuthorize("hasAuthority('USERS_CREATE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Dto.OrgMemberDto> addMember(@Valid @RequestBody Dto.CreateOrgMemberRequest req) {
        return ResponseEntity.status(201).body(organizationService.addMember(req));
    }
```

| Línea | Qué hace |
|-------|----------|
| `POST /organizations/me/members` | Invitar miembro (email, password, rol). |
| `status(201)` | HTTP 201 Created. |

```java
    @PatchMapping("/me/members/{id}")
    @PreAuthorize("hasAuthority('USERS_UPDATE') or hasRole('ORG_MANAGER')")
    public Dto.OrgMemberDto updateMember(@PathVariable String id, @RequestBody Dto.UpdateOrgMemberRequest req) {
        return organizationService.updateMember(id, req);
    }

    @DeleteMapping("/me/members/{id}")
    @PreAuthorize("hasAuthority('USERS_DELETE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Void> removeMember(@PathVariable String id) {
        organizationService.removeMember(id);
        return ResponseEntity.noContent().build();
    }
}
```

| Línea | Qué hace |
|-------|----------|
| `@PathVariable String id` | ID del miembro en la URL. |
| `removeMember` | Borra miembro (OrgTeamPage `remove()`). |
| `noContent()` | HTTP 204, body vacío. |

---

## 3. ProductController #########################

**Archivo:** `ProductController.java`  
**Ruta base:** `/products`  
**Frontend:** `InventoryPage`, `DashboardPage`, `AlertsPage`, modales

### Qué hace en la aplicación

**Corazón del inventario**: catálogo, filtros, CRUD, consumir y reponer stock.

| Funcionalidad en la app | Endpoint | UI |
|-------------------------|----------|-----|
| Listar con filtros | `GET /products` | InventoryPage |
| Crear / editar / borrar | POST, PATCH, DELETE | ProductModal, grid |
| Consumir / reponer | POST consume, restock | AdjustModal |
| Historial movimientos | `GET /{id}/movements` | Detalle producto |

### Controller → Service

```
ProductController          →    ProductService (CRUD, stock, dashboard data)
                           →    ProductAliasService (nombres WhatsApp)
```

### Qué hace ProductService

| Método | Lógica |
|--------|--------|
| **listFiltered** | Productos de la org + filtros q, category, lowStock, expiringSoon. |
| **create / update / delete** | SKU único por org; solo productos owned. |
| **consume / restock** | Ajusta stock + logs; restock con precio → registra compra. |
| **getDashboard** | KPIs y listas alertas (usado por DashboardController). |

```java
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductAliasService productAliasService;
```

| Línea | Qué hace |
|-------|----------|
| Dos services | Productos CRUD + aliases (WhatsApp/nombres alternativos). |

```java
    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ') or hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public List<Dto.ProductResponse> list(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(required = false) Boolean expiringSoon,
            @RequestParam(required = false) Integer stagnantDays,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        return productService.listFiltered(lowStock, expiringSoon, stagnantDays, category, q);
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /products` | Lista con filtros opcionales. |
| `@RequestParam(required = false)` | Query params opcionales; si no vienen, son `null`. |
| `q` | Búsqueda por nombre/SKU/barcode → InventoryPage `search`. |
| `lowStock`, `expiringSoon` | Toggles de filtros en frontend. |
| `listFiltered(...)` | Service aplica filtros **solo de la org del usuario**. |

```java
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public Dto.ProductResponse get(@PathVariable String id, @AuthenticationPrincipal String userId) {
        return productService.getById(id, userId);
    }

    @GetMapping("/{id}/movements")
    @PreAuthorize("...")
    public List<Dto.ProductMovementDto> movements(
            @PathVariable String id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return productService.movements(id, from, to);
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /products/{id}` | Detalle de un producto. |
| `GET /products/{id}/movements` | Historial consumos/reposiciones. |
| `LocalDate from/to` | Rango de fechas opcional. |

```java
    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_CREATE') or hasAnyRole('ORG_MANAGER','ORG_MEMBER')")
    public ResponseEntity<Dto.ProductResponse> create(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody Dto.CreateProductRequest req) {
        return ResponseEntity.status(201).body(productService.create(userId, req));
    }
```

| Línea | Qué hace |
|-------|----------|
| `POST /products` | Crear producto → ProductModal create. |
| `userId` | Auditoría: quién creó. |
| `201` | Recurso creado. |

```java
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE') or hasAnyRole('ORG_MANAGER','ORG_MEMBER')")
    public Dto.ProductResponse update(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @RequestBody Dto.UpdateProductRequest req) {
        return productService.update(id, userId, req);
    }
```

| Línea | Qué hace |
|-------|----------|
| `PATCH /products/{id}` | Editar campos parciales del producto. |

```java
    @PostMapping("/{id}/adjust")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE') or hasAnyRole('ORG_MANAGER','ORG_MEMBER')")
    public Dto.ProductResponse adjust(
            @PathVariable String id,
            @Valid @RequestBody Dto.AdjustStockRequest req) {
        return productService.adjust(id, req);
    }
```

| Línea | Qué hace |
|-------|----------|
| Ajuste genérico de stock | +/- cantidad con motivo. |

```java
    @PostMapping("/{id}/consume")
    @PreAuthorize("...")
    public Dto.ProductResponse consume(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @RequestBody Dto.ConsumeRequest req) {
        return productService.consume(id, userId, req);
    }

    @PostMapping("/{id}/restock")
    @PreAuthorize("...")
    public Dto.ProductResponse restock(
            @PathVariable String id,
            @AuthenticationPrincipal String userId,
            @RequestBody Dto.ConsumeRequest req) {
        return productService.restock(id, userId, req);
    }
```

| Línea | Qué hace |
|-------|----------|
| `POST .../consume` | Resta stock → **AdjustModal** modo consume. |
| `POST .../restock` | Suma stock + puede registrar compra → **AdjustModal** restock. |
| Mismo DTO `ConsumeRequest` | Cantidad, notas, proveedor opcional en restock. |

```java
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_DELETE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal String userId) {
        productService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
```

| Línea | Qué hace |
|-------|----------|
| `DELETE /products/{id}` | Borrar producto → `deleteProduct.mutate(id)` en frontend. |
| `204 No Content` | Éxito sin JSON en body. |

---

## 4. DashboardController  #######################################

**Archivo:** `DashboardController.java`  
**Ruta base:** `/dashboard`  
**Frontend:** `DashboardPage`, `AlertsPage` (`useDashboard`)

### Qué hace en la aplicación

Alimenta el **panel principal**: KPIs operativos y financieros.

| Endpoint | Qué muestra | Service |
|----------|-------------|---------|
| `GET /dashboard` | Totales, stock bajo, por vencer, listas | ProductService.getDashboard |
| `GET /dashboard/executive` | Valor stock, compras mes, estancados | ExecutiveDashboardService.executive |

### Qué hace cada Service

- **ProductService.getDashboard** — Separa productos con alertas para Dashboard/AlertsPage.
- **ExecutiveDashboardService** — Suma valor inventario y gasto mensual desde compras.

```java
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProductService productService;
    private final ExecutiveDashboardService executiveDashboardService;
```

| Línea | Qué hace |
|-------|----------|
| Dos services | Operativo (alertas) vs ejecutivo (finanzas). |

```java
    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ') or hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public Dto.DashboardResponse dashboard(@AuthenticationPrincipal String userId) {
        return productService.getDashboard(userId);
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /dashboard` | KPIs: totalProducts, lowStockCount, expiringCount, listas de productos. |
| `getDashboard(userId)` | Agrega datos de la org del usuario. |

```java
    @GetMapping("/executive")
    @PreAuthorize("hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public Dto.ExecutiveDashboardDto executive() {
        return executiveDashboardService.executive();
    }
}
```

| Línea | Qué hace |
|-------|----------|
| `GET /dashboard/executive` | Valor stock, compras del mes, productos estancados. |
| DashboardPage | Segunda query React Query con key `['executive']`. |

---

## 5. PurchaseController       #####################

**Archivo:** `PurchaseController.java`  
**Ruta base:** `/purchases`  
**Frontend:** `PurchasesPage`

### Qué hace en la aplicación

**Historial de compras** y gasto del periodo. Las filas también se crean **automáticamente** al reponer stock con precio (AdjustModal → ProductService → PurchaseRecordService).

| Endpoint | Pantalla |
|----------|----------|
| `GET /purchases` | Tabla + card "$ gasto del periodo" |
| `POST /purchases` | Compra manual (API) |

### Controller → Service

```
PurchaseController  →  PurchaseRecordService.listFiltered() / createManual()
```

### Qué hace PurchaseRecordService

- **listFiltered** — Compras de la org (default últimos 30 días) + `periodTotalSpend`.
- **attachToRestockIfPriced** — Llamado al reponer: crea fila compra si hay unitPrice.

```java
@RestController
@RequestMapping("/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseRecordService purchaseRecordService;
```

| Línea | Qué hace |
|-------|----------|
| Un service | Registro y listado de compras/reposiciones. |

```java
    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASES_CREATE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Dto.PurchaseRowDto> record(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody Dto.CreatePurchaseRequest body) {
        return ResponseEntity.status(201).body(purchaseRecordService.createManual(userId, body));
    }
```

| Línea | Qué hace |
|-------|----------|
| `POST /purchases` | Registrar compra manual (no solo vía restock). |
| `createManual` | Persiste fila en historial de compras. |

```java
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
```

| Línea | Qué hace |
|-------|----------|
| `GET /purchases` | **PurchasesPage** → `purchaseService.list()`. |
| Retorna `PurchasesPageDto` | `{ items: [...], periodTotalSpend: number }`. |
| Filtros opcionales | Por producto o rango de fechas. |

---

## 6. SupplierController          ###################

**Archivo:** `SupplierController.java`  
**Ruta base:** `/suppliers`  
**Frontend:** `SuppliersPage`, `AdjustModal` (restock)

### Qué hace en la aplicación

Catálogo de **proveedores** de la org para asociar al reponer stock.

| Endpoint | UI |
|----------|-----|
| GET / POST / PATCH / DELETE | SuppliersPage + dropdown en restock |

### Controller → Service

```
SupplierController  →  SupplierManagementService (list, create, update, delete por orgId)
```

```java
@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierManagementService supplierManagementService;
```

```java
    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASES_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public List<Dto.SupplierDto> list(@AuthenticationPrincipal String userId) {
        return supplierManagementService.list(userId);
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /suppliers` | Lista proveedores de la org. |

```java
    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASES_CREATE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Dto.SupplierDto> create(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody Dto.CreateSupplierRequest body) {
        return ResponseEntity.status(201).body(supplierManagementService.create(userId, body));
    }
```

| Línea | Qué hace |
|-------|----------|
| `POST /suppliers` | Crear proveedor → SuppliersPage form. |

```java
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASES_UPDATE') or hasRole('ORG_MANAGER')")
    public Dto.SupplierDto update(...) { ... }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASES_DELETE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Void> delete(...) {
        supplierManagementService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
```

| Línea | Qué hace |
|-------|----------|
| CRUD completo | PATCH editar, DELETE borrar proveedor. |

---

## 7. ReportInsightsController  ###############################

**Archivo:** `ReportInsightsController.java`  
**Ruta base:** `/reports`  
**Frontend:** `StatsPage`

### Qué hace en la aplicación

**Analítica**: rotación, valor por categoría, export Excel.

| Endpoint | StatsPage |
|----------|-----------|
| `/reports/inventory` | StatCards |
| `/reports/rotation` | Tabla top consumo |
| `/reports/export` | Botón XLSX |

### Controller → Service

```
ReportInsightsController  →  InventoryReportInsightsService (métricas)
                           →  ReportExportService (Excel POI)
                           →  InventorySnapshotService (historial)
```

```java
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportInsightsController {

    private final InventoryReportInsightsService reportInsightsService;
    private final ReportExportService reportExportService;
    private final InventorySnapshotService snapshotService;
```

| Línea | Qué hace |
|-------|----------|
| Tres services | Insights, export Excel, snapshots históricos. |

```java
    @GetMapping("/rotation")
    @PreAuthorize("hasAuthority('REPORTS_READ') or hasAnyRole('ORG_MANAGER','ORG_MEMBER','ORG_VIEWER')")
    public Dto.RotationReportDto rotation(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reportInsightsService.rotation(userId, from, to);
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /reports/rotation` | StatsPage → top consumo 30 días. |

```java
    @GetMapping("/inventory")
    @PreAuthorize("...")
    public Dto.InventoryReportDto inventory(@AuthenticationPrincipal String userId) {
        return reportInsightsService.inventoryOverview(userId);
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /reports/inventory` | Total SKU, valor, estancados, byCategory. |

```java
    @GetMapping("/by-category")
    public List<Dto.CategoryBreakdownDto> byCategory(@AuthenticationPrincipal String userId) { ... }

    @GetMapping("/by-supplier")
    public List<Dto.SupplierSpendRowDto> bySupplier(...) { ... }

    @GetMapping("/by-channel")
    public List<Dto.ChannelReportRowDto> byChannel(...) { ... }

    @GetMapping("/history")
    public List<Dto.InventorySnapshotDto> history(...) {
        return snapshotService.history(from, to);
    }
```

| Endpoint | Qué devuelve |
|----------|--------------|
| `/by-category` | Desglose por categoría |
| `/by-supplier` | Gasto por proveedor |
| `/by-channel` | Canal (web, WhatsApp, etc.) |
| `/history` | Snapshots históricos de inventario |

```java
    @GetMapping("/export")
    @PreAuthorize("...")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "xlsx") String format) {
        if (!"xlsx".equalsIgnoreCase(format)) {
            return ResponseEntity.badRequest().build();
        }
        byte[] data = reportExportService.exportXlsx(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-inventario.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}
```

| Línea | Qué hace |
|-------|----------|
| `GET /reports/export` | StatsPage botón "Exportar XLSX". |
| `byte[]` | Archivo binario, no JSON. |
| `CONTENT_DISPOSITION` | Header para forzar descarga con nombre. |
| `contentType` | MIME type Excel `.xlsx`. |
| `badRequest()` | Si format ≠ xlsx → HTTP 400. |

---

## 8. AdminController                   ####################

**Archivo:** `AdminController.java`  
**Ruta base:** `/admin`  
**Frontend:** `AdminRolesPage`  
**Acceso:** solo `PLATFORM_OWNER` (a nivel de clase)

### Qué hace en la aplicación

**Super-admin**: matriz RBAC, usuarios globales, mantenimiento, aprobar orgs.

| Endpoint | AdminRolesPage |
|----------|----------------|
| `/admin/rbac`, PUT permissions | Checkboxes permisos |
| `/admin/users`, POST, PATCH role | Crear/editar usuarios |
| `/admin/maintenance` | Toggle mantenimiento |

### Controller → Service

```
AdminController  →  AdminRbacService (matriz permisos)
                  →  AdminUserService (usuarios CRUD)
                  →  AdminOrgService (aprobar orgs)
                  →  MaintenanceState (flag global)
```

### Qué hace cada Service

- **AdminRbacService** — Define qué rol puede INVENTORY_CREATE, REPORTS_READ, etc.
- **AdminUserService** — Crea usuarios PLATFORM_OWNER o de org con validaciones.
- **AdminOrgService** — Aprueba/rechaza tenants PENDING.

```java
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_OWNER')")
public class AdminController {

    private final AdminRbacService adminRbacService;
    private final AdminUserService adminUserService;
    private final AdminOrgService adminOrgService;
    private final MaintenanceState maintenanceState;
```

| Línea | Qué hace |
|-------|----------|
| `@PreAuthorize` en **clase** | **Todos** los métodos exigen PLATFORM_OWNER. |
| Cuatro dependencias | RBAC, usuarios, orgs pendientes, mantenimiento. |

```java
    @GetMapping("/rbac")
    public Dto.RbacMatrixResponse getRbac() {
        return adminRbacService.getMatrix();
    }

    @GetMapping("/roles")
    public List<Dto.AdminRoleDto> listRoles() {
        return adminRbacService.listRoles();
    }

    @PutMapping("/rbac/permissions")
    public ResponseEntity<Void> updateRbacPermissions(@Valid @RequestBody Dto.RbacBatchUpdateRequest body) {
        adminRbacService.updatePermissionsBatch(body.getCells());
        return ResponseEntity.ok().build();
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /admin/rbac` | Matriz roles × módulos × CRUD. |
| `GET /admin/roles` | Lista roles para selects. |
| `PUT /admin/rbac/permissions` | Guarda checkboxes → `savePerms.mutate()`. |

```java
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
```

| Línea | Qué hace |
|-------|----------|
| CRUD usuarios global | Crear user, cambiar rol/org. |
| `PATCH .../role` | `patchRole.mutate()` en frontend. |

```java
    @GetMapping("/maintenance")
    public Map<String, Boolean> getMaintenance() {
        return Map.of("enabled", maintenanceState.isEnabled());
    }

    @PutMapping("/maintenance")
    public ResponseEntity<Void> setMaintenance(@Valid @RequestBody Dto.MaintenanceToggleRequest body) {
        maintenanceState.setEnabled(body.isEnabled());
        return ResponseEntity.ok().build();
    }
```

| Línea | Qué hace |
|-------|----------|
| GET/PUT maintenance | Toggle modo mantenimiento global. |
| `MaintenanceState` | Bean singleton en memoria (compartido con AuthController). |

```java
    @GetMapping("/organizations")
    public List<Dto.PendingOrgDto> listOrganizations(
            @RequestParam(defaultValue = "PENDING") String status) {
        return adminOrgService.listByStatus(status);
    }

    @PostMapping("/organizations/{orgId}/review")
    public ResponseEntity<Void> reviewOrganization(
            @PathVariable String orgId,
            @Valid @RequestBody Dto.OrgApprovalRequest req) {
        adminOrgService.review(orgId, req);
        return ResponseEntity.ok().build();
    }
}
```

| Línea | Qué hace |
|-------|----------|
| Orgs pendientes | Aprobar/rechazar onboarding (flujo B2B). |
| `defaultValue = "PENDING"` | Si no pasas status, filtra pendientes. |

---

## 9. PlatformController       #######################

**Archivo:** `PlatformController.java`  
**Ruta base:** `/platform`  
**Frontend:** `PlatformAdminPage`, `AdminRolesPage` (lista orgs)  
**Acceso:** solo `PLATFORM_OWNER`

### Qué hace en la aplicación

Vista **global** multi-tenant: todas las empresas, usuarios y límite de miembros (+5/-5).

### Controller → Service

```
PlatformController  →  PlatformService.listOrganizations()
                    →  PlatformService.listUsers()
                    →  PlatformService.updateMaxMembers()
```

### Qué hace PlatformService

Lista **todas** las orgs/users (sin filtro tenant) y actualiza cupo `maxMembers` por empresa.

```java
@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_OWNER')")
public class PlatformController {

    private final PlatformService platformService;
```

```java
    @GetMapping("/organizations")
    public List<Dto.PlatformOrganizationRowDto> organizations() {
        return platformService.listOrganizations();
    }

    @GetMapping("/users")
    public List<Dto.PlatformUserRowDto> users() {
        return platformService.listUsers();
    }
```

| Línea | Qué hace |
|-------|----------|
| `GET /platform/organizations` | Tab orgs en PlatformAdminPage. |
| `GET /platform/users` | Tab usuarios global. |
| También usado en AdminRolesPage | Select organización al crear usuario. |

```java
    @PatchMapping("/organizations/{id}/max-members")
    public Dto.PlatformOrganizationRowDto maxMembers(
            @PathVariable String id,
            @Valid @RequestBody Dto.MaxMembersRequest req) {
        return platformService.updateMaxMembers(id, req.getMaxMembers());
    }
}
```

| Línea | Qué hace |
|-------|----------|
| `PATCH /platform/organizations/{id}/max-members` | Botones +5 / -5 límite miembros. |
| Body `{ maxMembers: N }` | Nuevo límite calculado en frontend. |

---

## 10. Mapa frontend ↔ backend

| Frontend | Service API | Controller | Endpoint principal |
|----------|-------------|------------|-------------------|
| AuthPage | authService | AuthController | POST `/auth/login`, `/register` |
| OnboardingPage | organizationService | OrganizationController | POST `/organizations` |
| OrgTeamPage | organizationService | OrganizationController | GET/POST/DELETE `/organizations/me/members` |
| InventoryPage | productService | ProductController | GET `/products?q=...` |
| DashboardPage / AlertsPage | dashboardService | DashboardController | GET `/dashboard` |
| DashboardPage (KPIs $) | dashboardService | DashboardController | GET `/dashboard/executive` |
| PurchasesPage | purchaseService | PurchaseController | GET `/purchases` |
| SuppliersPage | supplierService | SupplierController | GET/POST `/suppliers` |
| StatsPage | reportService | ReportInsightsController | GET `/reports/inventory`, `/rotation`, `/export` |
| AdminRolesPage | adminService | AdminController | `/admin/rbac`, `/users`, `/maintenance` |
| PlatformAdminPage | platformService | PlatformController | `/platform/organizations`, `/users` |

### Patrón común en todos

```
@PreAuthorize  →  ¿Puede entrar?
Controller     →  Recibe HTTP, valida @Valid
Service        →  Reglas de negocio + tenant (orgId)
ResponseEntity →  Status HTTP + JSON
```

### Controllers no incluidos (menos centrales para la demo)

| Controller | Motivo |
|------------|--------|
| CategoryController | CRUD categorías (usado en ProductModal) |
| WhatsAppController | Webhook Twilio, no UI |
| HealthController | Health check `/health` |
| ProductImportController | Import masivo Excel |
| WarehouseController | Multi-almacén (si aplica) |

---

## 11. Resumen: Controller + Service + funcionalidad en la app

Tabla única para exposición: **qué controller**, **qué service llama**, **qué hace en la app**.

| Controller | Service(s) principal(es) | Qué hace el Service | Funcionalidad en la app |
|------------|--------------------------|---------------------|-------------------------|
| **AuthController** | AuthService, PasswordResetService | Login/register, JWT, permisos; reset password | Entrar al sistema (AuthPage) |
| **OrganizationController** | OrganizationService | Crear tenant, equipo, miembros, JWT post-onboarding | Onboarding + Equipo (OrgTeamPage) |
| **ProductController** | ProductService, ProductAliasService | CRUD productos, stock, consumo, reposición, filtros | Inventario completo + modales |
| **DashboardController** | ProductService, ExecutiveDashboardService | KPIs alertas + métricas financieras | Dashboard + Alertas |
| **PurchaseController** | PurchaseRecordService | Historial compras, gasto periodo, registro al reponer | Página Compras |
| **SupplierController** | SupplierManagementService | CRUD proveedores por org | Proveedores + restock |
| **ReportInsightsController** | InventoryReportInsightsService, ReportExportService, InventorySnapshotService | Rotación, categorías, Excel | Reportes / StatsPage |
| **AdminController** | AdminRbacService, AdminUserService, AdminOrgService | Matriz RBAC, usuarios global, mantenimiento | Admin RBAC (solo owner plataforma) |
| **PlatformController** | PlatformService | Vista global orgs/users, límite miembros | Admin plataforma |

### Flujo completo de ejemplo (eliminar producto)

```
Usuario en AlertsPage → click Eliminar → confirm
       ↓
Frontend: deleteProduct.mutate(id)
       ↓
HTTP DELETE /api/products/{id}  +  Authorization: Bearer JWT
       ↓
Spring Security: valida JWT, extrae userId y orgId
       ↓
ProductController.delete(id, userId)
       ↓  @PreAuthorize INVENTORY_DELETE
ProductService.delete(id, userId)
       ↓  findOwned → producto.orgId == JWT.orgId
       ↓  productRepo.delete(...)
       ↓
HTTP 204 No Content
       ↓
Frontend: onSuccess → syncProductQueries → dashboard e inventario se actualizan
```

### Capas y responsabilidades (para la exposición)

```
┌─────────────────────────────────────────────────────────┐
│  FRONTEND (React)                                       │
│  Pages → api.ts → fetch con JWT                         │
└───────────────────────────┬─────────────────────────────┘
                            │ HTTP JSON
┌───────────────────────────▼─────────────────────────────┐
│  CONTROLLER                                             │
│  Ruta + permiso + validar DTO + llamar 1 método service   │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│  SERVICE                                                │
│  Reglas negocio + orgId (tenant) + transacciones        │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│  REPOSITORY (JPA)                                       │
│  SQL / entidades Product, User, Organization, Purchase… │
└─────────────────────────────────────────────────────────┘
```

---

*Documento generado para exposición del backend Spring Boot — 9 controllers principales.*
