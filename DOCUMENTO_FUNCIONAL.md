# SmartHome Shopper — Documento funcional del backend

Este documento describe **qué problema resuelve** la API y **cómo usar cada endpoint** de forma práctica (método, URL, autenticación, cuerpos y respuestas).

---

## 1. Qué hace este backend

La aplicación es una **API REST** para **inventario doméstico por usuario**:

- Cada usuario se registra e inicia sesión; recibe un **JWT** para las peticiones siguientes.
- Puede **crear, listar, consultar, actualizar y borrar productos** (nombre, cantidad, mínimo para alerta, unidad, caducidad, etc.).
- Puede **registrar consumo** (bajar stock) y **reposición** (subir stock); cada operación puede dejar trazas en base de datos (`ConsumptionLog`).
- Un **dashboard** resume cuántos productos hay, cuántos van con **stock bajo** y cuántos **vencen pronto** (7 días), además de listas filtradas.
- Un **webhook de WhatsApp** (pensado para **Twilio**) recibe mensajes: responde con comandos cortos, consumo rápido con prefijo `-`, o intenta interpretar texto libre con **OpenAI** y actualizar inventario.

**Requisitos de entorno:** PostgreSQL accesible según `application.yml`; para la rama IA de WhatsApp, una **API key válida de OpenAI**.

**URL base:** todas las rutas de esta API llevan el prefijo del servidor y el *context path*:

`http://localhost:8080/api`

En los ejemplos siguientes, `{BASE}` = `http://localhost:8080/api`.

---

## 2. Autenticación

- Endpoints bajo `/auth` y `/webhook` son **públicos** (no exigen JWT).
- El resto exige cabecera:

```http
Authorization: Bearer <token>
```

El token lo devuelven `POST /auth/register` y `POST /auth/login` en el campo `token`.

Si falta el token o no es válido, Spring Security responde **401 Unauthorized** (no entra en la lógica del controlador).

**Formato de datos:** salvo el webhook de WhatsApp (formulario), los cuerpos son **JSON** (`Content-Type: application/json`).

---

## 3. Valores de unidad de producto

Al crear o actualizar productos, el campo `unit` usa el enum (en JSON, como string):

`UNIT`, `KG`, `LITER`, `GRAM`, `ML`, `PACK`

---

## 4. Catálogo de endpoints

### 4.1 Autenticación

#### `POST {BASE}/auth/register`

**Función:** Crear cuenta, guardar usuario (contraseña hasheada) y devolver JWT.

**Autenticación:** No.

**Cuerpo (JSON):**

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `email` | Sí | Email válido, único en el sistema |
| `password` | Sí | Mínimo 6 caracteres |
| `name` | Sí | Nombre del usuario |
| `whatsappNumber` | No | Teléfono para enlazar mensajes de WhatsApp (debe coincidir con el formato que envía Twilio al quitar el prefijo `whatsapp:`) |

**Respuesta:** `200 OK` — objeto `AuthResponse`:

- `token`, `userId`, `name`, `email`

**Errores habituales:** `400` validación; `409` si el email ya está registrado (mensaje en JSON `{"error":"..."}` vía manejador global).

---

#### `POST {BASE}/auth/login`

**Función:** Validar email y contraseña y devolver JWT.

**Autenticación:** No.

**Cuerpo (JSON):**

| Campo | Obligatorio |
|--------|-------------|
| `email` | Sí |
| `password` | Sí |

**Respuesta:** `200 OK` — mismo formato que `AuthResponse` en registro.

**Errores habituales:** `400` con mensaje genérico de credenciales inválidas (no distingue “usuario no existe” de “contraseña incorrecta” en el texto expuesto).

---

### 4.2 Productos

Todos requieren **JWT** válido.

#### `GET {BASE}/products`

**Función:** Listar todos los productos del usuario autenticado.

**Respuesta:** `200 OK` — array de objetos `ProductResponse` (ver sección 5).

---

#### `GET {BASE}/products/{id}`

**Función:** Obtener un producto por id si pertenece al usuario.

**Respuesta:** `200 OK` — un `ProductResponse`.

**Errores:** `404` producto no existe; `403` producto de otro usuario.

---

#### `POST {BASE}/products`

**Función:** Crear producto para el usuario autenticado.

**Cuerpo (JSON):**

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `name` | Sí | Nombre del producto |
| `quantity` | Sí | Cantidad actual ≥ 0 |
| `minQuantity` | Sí | Umbral de stock bajo ≥ 0 |
| `unit` | Sí | Ver sección 3 |
| `consumptionPerUse` | No | Por defecto en lógica de negocio se usa `1.0` si no se envía |
| `expiryDate` | No | Fecha `YYYY-MM-DD` |
| `barcode`, `category`, `imageUrl` | No | Opcionales |

**Respuesta:** `201 Created` — `ProductResponse`.

**Errores:** `400` validación; `400` si el usuario del token no existe en BD.

---

#### `PATCH {BASE}/products/{id}`

**Función:** Actualizar solo los campos enviados (parcial).

**Cuerpo (JSON):** todos opcionales entre: `name`, `quantity`, `minQuantity`, `unit`, `consumptionPerUse`, `expiryDate`, `category`.

**Respuesta:** `200 OK` — `ProductResponse`.

**Errores:** `404` / `403` como en GET por id.

---

#### `POST {BASE}/products/{id}/consume`

**Función:** Reducir la cantidad del producto y registrar un log de consumo (origen WEB).

**Cuerpo (JSON):**

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `amount` | Sí* | Cantidad a restar; en el modelo está anotada como positiva |
| `note` | No | Nota libre |

\*En el controlador actual **no** se aplica `@Valid` automáticamente; conviene enviar siempre `amount` numérico positivo. Si se envía otro valor, el comportamiento puede ser inesperado.

**Respuesta:** `200 OK` — `ProductResponse` actualizado (la cantidad no baja de 0).

---

#### `POST {BASE}/products/{id}/restock`

**Función:** Aumentar la cantidad y registrar log de reposición (origen WEB).

**Cuerpo:** mismo esquema que `consume` (`amount`, `note` opcional). Misma observación sobre validación automática que en `consume`.

**Respuesta:** `200 OK` — `ProductResponse`.

---

#### `DELETE {BASE}/products/{id}`

**Función:** Eliminar el producto si es del usuario.

**Respuesta:** `204 No Content`.

**Errores:** `404` / `403`.

---

### 4.3 Dashboard

#### `GET {BASE}/dashboard`

**Función:** Resumen de inventario para el usuario autenticado.

**Autenticación:** JWT.

**Respuesta:** `200 OK` — objeto `DashboardResponse`:

- `totalProducts` — número total de productos
- `lowStockCount` — cuántos están en stock bajo (`quantity <= minQuantity`)
- `expiringCount` — cuántos vencen en los próximos 7 días
- `lowStockProducts` — lista de `ProductResponse` en bajo stock
- `expiringProducts` — lista de `ProductResponse` por vencer
- `allProducts` — lista completa

Los flags `lowStock` y `expiringSoon` en cada `ProductResponse` son coherentes con estas reglas.

---

### 4.4 Webhook WhatsApp (Twilio)

#### `POST {BASE}/webhook/whatsapp`

**Función:** Punto al que Twilio envía mensajes entrantes de WhatsApp. La aplicación genera una respuesta de texto y la devuelve como **XML TwiML** (`<Response><Message>…</Message></Response>`).

**Autenticación:** No (debe protegerse en producción, por ejemplo validando firma de Twilio o restringiendo IP).

**Content-Type:** `application/x-www-form-urlencoded`.

**Parámetros formales usados en código:**

| Parámetro | Descripción |
|-----------|-------------|
| `From` | Identificador del remitente (ej. `whatsapp:+519998887766`) |
| `Body` | Texto del mensaje |

**Comportamiento funcional resumido:**

1. Se normaliza el teléfono (se quita `whatsapp:`) y se busca un usuario con ese `whatsappNumber`.
2. Si no hay usuario: respuesta fija invitando a registrarse con ese número.
3. Comandos (ej. `inventario`, `stock`, `lista`): resumen de inventario.
4. Comandos (ej. `alertas`, `bajos`): bajo stock y próximos a vencer.
5. Mensaje que empieza por `-`: consumo rápido (ej. `-leche` o `-arroz 0.5`).
6. Cualquier otro texto: se envía a **OpenAI**; se espera JSON con acciones `add` / `consume` / etc. y se actualiza inventario según corresponda. Sin API key válida, el usuario recibe un mensaje de error amigable.

**Respuesta:** `200 OK` con cuerpo **XML** (TwiML), no JSON.

---

## 5. Formato `ProductResponse` (referencia)

Objeto JSON típico devuelto en listados y detalle de producto:

| Campo | Tipo | Descripción |
|--------|------|-------------|
| `id` | string | UUID del producto |
| `name` | string | Nombre |
| `quantity` | number | Cantidad actual |
| `minQuantity` | number | Umbral mínimo |
| `unit` | string | Nombre del enum (ej. `LITER`) |
| `consumptionPerUse` | number | Consumo por uso |
| `expiryDate` | string o null | Fecha ISO fecha (`YYYY-MM-DD`) |
| `barcode`, `category`, `imageUrl` | string o null | Opcionales |
| `lowStock` | boolean | `quantity <= minQuantity` |
| `expiringSoon` | boolean | caduca en ≤ 7 días |
| `daysUntilEmpty` | number o null | Estimación de días hasta agotarse según consumo reciente en logs |
| `createdAt`, `updatedAt` | string o null | Fecha-hora ISO |

---

## 6. Errores genéricos (API JSON)

Para muchas excepciones `RuntimeException`, un `@RestControllerAdvice` devuelve JSON:

```json
{ "error": "mensaje descriptivo" }
```

Códigos aproximados según el texto del mensaje:

- Contiene `not found` → **404**
- Contiene `Forbidden` → **403**
- Contiene `already` → **409**
- Otros → **400**

Los errores de validación Bean Validation pueden tener formato distinto según configuración; conviene probar con payloads inválidos en desarrollo.

---

## 7. Ejemplo mínimo de flujo

1. `POST /api/auth/register` con email, password, name.
2. Guardar `token` de la respuesta.
3. `GET /api/products` con cabecera `Authorization: Bearer <token>`.
4. `POST /api/products` con JWT y JSON de producto para crear ítems.
5. `GET /api/dashboard` con el mismo JWT para ver resumen.

---

*Documento alineado con el código del módulo `com.smarthome` en este repositorio.*
