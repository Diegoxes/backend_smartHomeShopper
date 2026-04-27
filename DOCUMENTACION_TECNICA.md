# SmartHome Shopper — Documentación técnica

## Resumen

API REST en **Spring Boot 3.2** (Java 21) para gestión de inventario del hogar: productos por usuario, consumo y reposición, panel resumido y webhook de **WhatsApp (Twilio)** con enrutamiento por comandos y procesamiento de lenguaje natural vía **OpenAI**.

## Estructura del proyecto (Maven)

```
smarthome-shopper/
├── pom.xml
├── mvnw / mvnw.cmd             # Maven Wrapper (descarga Maven 3.9.x al primer uso; requiere JDK 21)
├── docker-compose.yml          # PostgreSQL local opcional
├── DOCUMENTACION_TECNICA.md
└── src/main/
    ├── java/com/smarthome/
    │   ├── SmartHomeShopperApplication.java
    │   ├── config/             # Seguridad, CORS, filtros JWT, RestTemplate
    │   ├── controller/         # REST: auth, productos, dashboard, webhook WhatsApp
    │   ├── dto/                # DTOs y requests/responses (clase Dto anidada)
    │   ├── entity/             # JPA: User, Product, ConsumptionLog
    │   ├── exception/          # Manejo global de errores
    │   ├── repository/         # Spring Data JPA
    │   └── service/            # Lógica de negocio, JWT, IA, WhatsApp
    └── resources/
        └── application.yml     # Datasource, JWT, OpenAI, Twilio, servidor
```

## Stack y dependencias principales

| Área | Tecnología |
|------|------------|
| Framework | Spring Boot Web, Validation |
| Persistencia | Spring Data JPA, Hibernate, PostgreSQL |
| Seguridad | Spring Security (stateless), JWT (jjwt 0.12.x), BCrypt |
| Integraciones | OpenAI Chat Completions (HTTP con RestTemplate), Twilio (webhook entrante; credenciales en yml para uso futuro) |
| Utilidades | Lombok |

## Modelo de datos (JPA)

- **User** (`users`): id (UUID), email único, password hasheado, name, `whatsapp_number`, `created_at`. Relación 1-N con productos.
- **Product** (`products`): id (UUID), usuario, nombre, cantidad, mínimo, unidad (`UNIT`, `KG`, `LITER`, etc.), consumo por uso, caducidad, código de barras, categoría, URL de imagen, timestamps. Métodos de dominio: `isLowStock()`, `isExpiringSoon()` (7 días).
- **ConsumptionLog** (`consumption_logs`): id, producto, cambio de cantidad (+/-), tipo de acción (`CONSUMED`, `RESTOCKED`, `ADJUSTED`), origen (`WEB`, `WHATSAPP`, `SYSTEM`), nota, `created_at`.

`spring.jpa.hibernate.ddl-auto: update` crea/actualiza esquema en desarrollo; en producción conviene migraciones explícitas (Flyway/Liquibase).

## Configuración y variables de entorno

Definidas en `application.yml` con valores por defecto. Sobrescribir con variables de entorno donde aplique:

| Variable / clave | Descripción |
|------------------|-------------|
| `DB_USERNAME`, `DB_PASSWORD` | Credenciales PostgreSQL |
| `spring.datasource.url` | JDBC (por defecto `localhost:5432/smarthome_db`) |
| `JWT_SECRET` | Clave HMAC para firmar JWT (debe ser suficientemente larga para el algoritmo) |
| `JWT` vía `jwt.expiration` | TTL del token en ms (por defecto 24 h) |
| `OPENAI_API_KEY` | API key de OpenAI para `AiService` |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` | Reservadas para integración Twilio (el webhook actual solo necesita la URL pública y el formulario que envía Twilio) |
| `twilio.whatsapp-from` | Número origen de WhatsApp en formato Twilio |

**Servidor:** puerto `8080`, `context-path: /api` → las rutas documentadas abajo son relativas a `http://localhost:8080/api`.

## Seguridad

- Rutas públicas: `/auth/**`, `/webhook/**`.
- Resto: header `Authorization: Bearer <JWT>`.
- El filtro JWT (`SecurityConfig`) extrae el **subject** del token como `userId` y lo expone como `@AuthenticationPrincipal String userId` en controladores.
- CORS permitido para `http://localhost:3000` y `http://localhost:5173`.

## API REST (resumen)

Todas las rutas bajo el prefijo `/api`.

### Autenticación (`/auth`)

- `POST /auth/register` — body: email, password (mín. 6), name, opcional `whatsappNumber`.
- `POST /auth/login` — body: email, password.

Respuesta (`AuthResponse`): `token`, `userId`, `name`, `email`.

### Productos (`/products`) — requiere JWT

- `GET /products` — listado del usuario autenticado.
- `GET /products/{id}` — detalle si el producto pertenece al usuario.
- `POST /products` — crear (`CreateProductRequest`: nombre, cantidades, unidad, etc.).
- `PATCH /products/{id}` — actualización parcial.
- `POST /products/{id}/consume` — body: `amount`, opcional `note` (registra log WEB).
- `POST /products/{id}/restock` — mismo body, suma cantidad.
- `DELETE /products/{id}` — borrado.

La respuesta de producto incluye flags `lowStock`, `expiringSoon` y `daysUntilEmpty` (estimación a partir del consumo medio diario en logs recientes).

### Dashboard (`/dashboard`) — requiere JWT

- `GET /dashboard` — totales, listas de bajo stock, por vencer y todos los productos.

### Webhook WhatsApp (`/webhook`)

- `POST /webhook/whatsapp` — `Content-Type: application/x-www-form-urlencoded`; parámetros típicos de Twilio: `From`, `Body`. Respuesta: XML TwiML con `<Message>`.

**Emparejamiento de usuario:** se normaliza `From` quitando el prefijo `whatsapp:` y se busca `User.whatsappNumber`. Debe coincidir el formato guardado en registro con el que envía Twilio (ej. `+51999999999`).

**Comportamiento del mensaje:**

1. Comandos: `inventario` / `stock` / `lista`; `alertas` / `bajos`.
2. Mensajes que empiezan por `-` consumo rápido (ej. `-leche`, `-arroz 0.5`).
3. Resto: `AiService` llama a OpenAI y espera JSON con `action`, `items`, `reply`; aplica altas/consumos sobre productos existentes o crea productos nuevos en altas.

## Servicios (lógica)

- **AuthService** — registro, login, codificación BCrypt, emisión JWT.
- **JwtService** — generar, validar y leer claims del token.
- **ProductService** — CRUD con comprobación de propiedad, consumo/reposición, logs, dashboard y cálculo de predicción de agotamiento vía `ConsumptionLogRepository.avgDailyConsumption`.
- **WhatsAppService** — orquestación del webhook y comandos cortos.
- **AiService** — prompt fijo + mensaje usuario → Chat Completions; parseo JSON y mutación de inventario.

## Errores

`GlobalExceptionHandler` traduce `RuntimeException` a HTTP según texto del mensaje: `not found` → 404, `Forbidden` → 403, `already` → 409, resto → 400. Cuerpo: `{"error":"..."}`.

## Cómo ejecutar en local

1. Base de datos: instalar PostgreSQL y crear `smarthome_db`, o ejecutar:
   ```bash
   docker compose up -d
   ```
2. Opcional: exportar `JWT_SECRET`, `OPENAI_API_KEY`, `DB_*` si no usas los valores por defecto del yml.
3. Compilar y arrancar (con JDK 21 instalado):
   ```bash
   ./mvnw spring-boot:run
   ```
   En Windows: `mvnw.cmd spring-boot:run`. Si ya tienes Maven instalado, también puedes usar `mvn spring-boot:run`.

La aplicación escucha en `http://localhost:8080/api`.

## Limitaciones y notas

- El TwiML de respuesta no escapa caracteres XML en el texto del mensaje; mensajes con `<`, `&`, etc. podrían romper el XML.
- La integración Twilio declarada en yml no envía mensajes salientes desde el código actual; solo se responde al webhook.
- Sin tests automatizados en el repositorio; conviene añadir tests de integración para repositorios y API.

---

*Documento generado a partir del estado del código del proyecto SmartHome Shopper.*
