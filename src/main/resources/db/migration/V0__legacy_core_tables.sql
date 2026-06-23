-- Tablas core del inventario original (antes del baseline SaaS V1).
-- Necesarias en BD vacía: V1 solo hace ALTER/INDEX sobre products y suppliers.

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    whatsapp_number VARCHAR(64),
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    quantity DOUBLE PRECISION NOT NULL DEFAULT 0,
    min_quantity DOUBLE PRECISION NOT NULL DEFAULT 0,
    unit VARCHAR(32) NOT NULL DEFAULT 'UNIT',
    consumption_per_use DOUBLE PRECISION,
    expiry_date DATE,
    barcode VARCHAR(100),
    category VARCHAR(255),
    image_url VARCHAR(512),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS suppliers (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(64),
    lead_time_days INTEGER,
    notes VARCHAR(2000),
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS consumption_logs (
    id VARCHAR(255) PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity_change DOUBLE PRECISION NOT NULL,
    action_type VARCHAR(32),
    source VARCHAR(32),
    note VARCHAR(500),
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS purchases (
    id VARCHAR(255) PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    supplier_id VARCHAR(255) REFERENCES suppliers(id),
    quantity DOUBLE PRECISION NOT NULL,
    unit_price NUMERIC(14, 4),
    total_amount NUMERIC(14, 2),
    currency VARCHAR(8),
    purchased_at TIMESTAMP NOT NULL,
    source VARCHAR(32),
    notes VARCHAR(500),
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product_aliases (
    id VARCHAR(255) PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    alias_text VARCHAR(255) NOT NULL,
    normalized_alias VARCHAR(255) NOT NULL,
    learned_whatsapp BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    CONSTRAINT uk_product_normalized_alias UNIQUE (product_id, normalized_alias)
);

CREATE TABLE IF NOT EXISTS whatsapp_pending_clarifications (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    payload_json TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_products_user ON products(user_id);
CREATE INDEX IF NOT EXISTS idx_suppliers_user ON suppliers(user_id);
CREATE INDEX IF NOT EXISTS idx_consumption_logs_product ON consumption_logs(product_id);
CREATE INDEX IF NOT EXISTS idx_purchases_product ON purchases(product_id);
