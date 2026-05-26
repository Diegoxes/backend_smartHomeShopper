-- Baseline schema for Inventario B2B (Flyway baseline-on-migrate for existing DBs)

CREATE TABLE IF NOT EXISTS organizations (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    industry VARCHAR(255),
    currency VARCHAR(16) NOT NULL DEFAULT 'MXN',
    country VARCHAR(64),
    timezone VARCHAR(64),
    max_members INTEGER NOT NULL DEFAULT 20,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS organization_settings (
    id VARCHAR(255) PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL UNIQUE REFERENCES organizations(id),
    expiry_alert_days INTEGER NOT NULL DEFAULT 7,
    prediction_horizon_days INTEGER NOT NULL DEFAULT 30
);

CREATE TABLE IF NOT EXISTS organization_members (
    id VARCHAR(255) PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL REFERENCES organizations(id),
    user_id VARCHAR(255) NOT NULL UNIQUE,
    org_role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS warehouses (
    id VARCHAR(255) PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS stock_levels (
    id VARCHAR(255) PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    warehouse_id VARCHAR(255) NOT NULL REFERENCES warehouses(id),
    quantity DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inventory_lots (
    id VARCHAR(255) PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    warehouse_id VARCHAR(255) NOT NULL REFERENCES warehouses(id),
    quantity DOUBLE PRECISION NOT NULL,
    expiry_date DATE,
    received_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory_snapshots (
    id VARCHAR(255) PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL REFERENCES organizations(id),
    snapshot_date DATE NOT NULL,
    total_value NUMERIC(14,2),
    breakdown_json TEXT,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(255) PRIMARY KEY,
    organization_id VARCHAR(255),
    user_id VARCHAR(255),
    action VARCHAR(128) NOT NULL,
    entity_type VARCHAR(128),
    entity_id VARCHAR(255),
    channel VARCHAR(32),
    detail VARCHAR(2000),
    created_at TIMESTAMP
);

ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS organization_id VARCHAR(255);
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS sku VARCHAR(64);
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS internal_code VARCHAR(64);
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS sale_price NUMERIC(14,4);
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS last_cost NUMERIC(14,4);
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(14,4);
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS purchase_unit VARCHAR(32);
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS units_per_purchase_unit DOUBLE PRECISION;

ALTER TABLE IF EXISTS suppliers ADD COLUMN IF NOT EXISTS organization_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_products_org ON products(organization_id);
CREATE INDEX IF NOT EXISTS idx_products_org_sku ON products(organization_id, sku);
CREATE INDEX IF NOT EXISTS idx_suppliers_org ON suppliers(organization_id);
