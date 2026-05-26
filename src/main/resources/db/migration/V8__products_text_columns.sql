-- Asegurar tipos texto en columnas de búsqueda (evita lower(bytea) en PostgreSQL).
ALTER TABLE products
    ALTER COLUMN sku TYPE VARCHAR(64) USING CAST(sku AS text),
    ALTER COLUMN barcode TYPE VARCHAR(100) USING CAST(barcode AS text),
    ALTER COLUMN name TYPE VARCHAR(255) USING CAST(name AS text),
    ALTER COLUMN category TYPE VARCHAR(255) USING CAST(category AS text);
