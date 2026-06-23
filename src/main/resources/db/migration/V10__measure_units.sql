-- Unidades de medida por organización y equivalencias por producto

CREATE TABLE IF NOT EXISTS measure_units (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    organization_id VARCHAR(255) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    base_unit BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_measure_units_org_code UNIQUE (organization_id, code)
);

CREATE INDEX IF NOT EXISTS idx_measure_units_org ON measure_units(organization_id);

CREATE TABLE IF NOT EXISTS product_uoms (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    product_id VARCHAR(255) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    measure_unit_id VARCHAR(255) NOT NULL REFERENCES measure_units(id) ON DELETE CASCADE,
    factor_to_base DOUBLE PRECISION NOT NULL CHECK (factor_to_base > 0),
    CONSTRAINT uq_product_uoms_product_unit UNIQUE (product_id, measure_unit_id)
);

CREATE INDEX IF NOT EXISTS idx_product_uoms_product ON product_uoms(product_id);

ALTER TABLE consumption_logs ADD COLUMN IF NOT EXISTS measure_unit_id VARCHAR(255) REFERENCES measure_units(id);
ALTER TABLE consumption_logs ADD COLUMN IF NOT EXISTS input_quantity DOUBLE PRECISION;

ALTER TABLE purchases ADD COLUMN IF NOT EXISTS measure_unit_id VARCHAR(255) REFERENCES measure_units(id);
ALTER TABLE purchases ADD COLUMN IF NOT EXISTS input_quantity DOUBLE PRECISION;
ALTER TABLE purchases ADD COLUMN IF NOT EXISTS cost_input_mode VARCHAR(32);

-- Semilla: unidad base + presentaciones comunes por cada org existente
INSERT INTO measure_units (id, organization_id, code, name, base_unit, active)
SELECT gen_random_uuid()::text, o.id, 'UNIT', 'Unidad', true, true
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM measure_units m WHERE m.organization_id = o.id AND m.code = 'UNIT'
);

INSERT INTO measure_units (id, organization_id, code, name, base_unit, active)
SELECT gen_random_uuid()::text, o.id, 'BOX', 'Caja', false, true
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM measure_units m WHERE m.organization_id = o.id AND m.code = 'BOX'
);

INSERT INTO measure_units (id, organization_id, code, name, base_unit, active)
SELECT gen_random_uuid()::text, o.id, 'PACK', 'Pack', false, true
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM measure_units m WHERE m.organization_id = o.id AND m.code = 'PACK'
);

INSERT INTO measure_units (id, organization_id, code, name, base_unit, active)
SELECT gen_random_uuid()::text, o.id, 'DOZEN', 'Docena', false, true
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM measure_units m WHERE m.organization_id = o.id AND m.code = 'DOZEN'
);

-- Migrar factor legacy units_per_purchase_unit → product_uoms (Caja)
INSERT INTO product_uoms (id, product_id, measure_unit_id, factor_to_base)
SELECT gen_random_uuid()::text, p.id, mu.id, p.units_per_purchase_unit
FROM products p
JOIN measure_units mu ON mu.organization_id = p.organization_id AND mu.code = 'BOX'
WHERE p.units_per_purchase_unit IS NOT NULL
  AND p.units_per_purchase_unit > 1
  AND NOT EXISTS (
      SELECT 1 FROM product_uoms pu WHERE pu.product_id = p.id AND pu.measure_unit_id = mu.id
  );
