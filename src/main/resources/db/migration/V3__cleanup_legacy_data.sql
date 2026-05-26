-- Script de limpieza para datos antiguos y configuración de roles/permisos

-- 1. Asegurar que todos los usuarios tengan un rol definido (si no tienen, asignar MEMBER por defecto)
UPDATE organization_members 
SET org_role = 'MEMBER' 
WHERE org_role IS NULL OR org_role = '';

-- 2. Eliminar miembros huérfanos (usuarios que ya no existen)
DELETE FROM organization_members om
WHERE NOT EXISTS (
    SELECT 1 FROM users u WHERE u.id = om.user_id
);

-- 3. Eliminar productos huérfanos (sin organización válida)
DELETE FROM products p
WHERE organization_id IS NULL 
   OR NOT EXISTS (
       SELECT 1 FROM organizations o WHERE o.id = p.organization_id
   );

-- 4. Asegurar que todos los productos tengan valores mínimos válidos
UPDATE products
SET 
    quantity = GREATEST(quantity, 0),
    min_quantity = GREATEST(min_quantity, 0)
WHERE quantity < 0 OR min_quantity < 0;

-- 5. Eliminar logs de consumo huérfanos
DELETE FROM consumption_logs cl
WHERE NOT EXISTS (
    SELECT 1 FROM products p WHERE p.id = cl.product_id
);

-- 6. Eliminar compras huérfanas
DELETE FROM purchases pu
WHERE NOT EXISTS (
    SELECT 1 FROM products p WHERE p.id = pu.product_id
);

-- 7. Asegurar que todas las organizaciones tengan configuración
INSERT INTO organization_settings (id, organization_id, expiry_alert_days, prediction_horizon_days)
SELECT 
    gen_random_uuid()::text,
    o.id,
    7,
    30
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM organization_settings os WHERE os.organization_id = o.id
)
ON CONFLICT (organization_id) DO NOTHING;

-- 8. Asegurar que todas las organizaciones tengan al menos un almacén por defecto
INSERT INTO warehouses (id, organization_id, name, is_default)
SELECT 
    gen_random_uuid()::text,
    o.id,
    'Almacén Principal',
    true
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM warehouses w WHERE w.organization_id = o.id
)
ON CONFLICT DO NOTHING;

-- 9. Actualizar fechas nulas con timestamp actual
UPDATE products
SET 
    created_at = COALESCE(created_at, NOW()),
    updated_at = COALESCE(updated_at, NOW())
WHERE created_at IS NULL OR updated_at IS NULL;

UPDATE organizations
SET created_at = COALESCE(created_at, NOW())
WHERE created_at IS NULL;

UPDATE organization_members
SET created_at = COALESCE(created_at, NOW())
WHERE created_at IS NULL;

-- 10. Log de limpieza completada
-- Esto no hace nada, pero queda como registro de que el script corrió
COMMENT ON TABLE products IS 'Limpieza de datos legacy completada en V3';
