# Plan técnico — Unidades de medida y stock en unidad base

> Implementación retail: compras en cajas/packs, stock y ventas en unidades.

## Principios

1. **Stock interno** siempre en unidad base (factor 1).
2. **Equivalencias** por organización y por producto (ej. Caja=24 gaseosa, Caja=6 leche).
3. **Costo flexible**: sugerencia `precio_caja / factor`, el usuario confirma costo unitario base.
4. **Precio de venta** independiente del costo.
5. Configuración por **organización**, no por usuario.

## Ejemplo retail

| Acción | Entrada | Resultado |
|--------|---------|-----------|
| Compra | 5 cajas × factor 24, costo S/4.50/unidad confirmado | +120 stock, compra S/540 |
| Venta | 3 unidades | −3 stock |

## API

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET/POST/PATCH/DELETE | `/measure-units` | Catálogo UOM por org |
| GET/PUT | `/products/{id}/uoms` | Equivalencias del producto |
| POST | `/products/{id}/restock` | `amount`, `measureUnitId`, `unitPrice` (base), `packagePrice`, `costInputMode` |
| POST | `/products/{id}/consume` | `amount`, `measureUnitId` opcional |

## DTOs clave

- `MeasureUnitDto`: id, code, name, baseUnit, active
- `ProductUomDto`: measureUnitId, code, name, factorToBase
- `ConsumeRequest`: amount, measureUnitId, unitPrice, packagePrice, costInputMode (PER_BASE | PER_PACKAGE)
- `ProductResponse`: quantity, stockBreakdown[], stockDisplay

## Pantallas afectadas

- ProductModal — presentaciones por producto
- AdjustModal — cantidad, unidad, costo caja/unidad
- MeasureUnitsPage — catálogo org
- ProductCard / Inventory — desglose stock
- PurchasesPage, Dashboard, Alerts — AdjustModal compartido
- WhatsApp, import Excel — Fase 6

## Legacy

- `products.units_per_purchase_unit` → migrado a `product_uoms`; lectura fallback 6 meses.
