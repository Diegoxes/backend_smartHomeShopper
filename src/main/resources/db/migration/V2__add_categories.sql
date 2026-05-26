-- Crear tabla de categorías personalizadas por organización
CREATE TABLE IF NOT EXISTS categories (
    id VARCHAR(255) PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    color_hex VARCHAR(7),
    created_at TIMESTAMP,
    UNIQUE(organization_id, name)
);

CREATE INDEX idx_categories_org ON categories(organization_id);

-- Insertar categorías por defecto para cada organización existente
INSERT INTO categories (id, organization_id, name, description, color_hex, created_at)
SELECT 
    gen_random_uuid()::text,
    o.id,
    cat.name,
    cat.description,
    cat.color_hex,
    NOW()
FROM organizations o
CROSS JOIN (VALUES 
    ('General', 'Productos generales', '#6B7280'),
    ('Alimentos', 'Productos alimenticios', '#10B981'),
    ('Bebidas', 'Bebidas y líquidos', '#3B82F6'),
    ('Limpieza', 'Productos de limpieza', '#8B5CF6'),
    ('Electrónica', 'Dispositivos y componentes electrónicos', '#F59E0B'),
    ('Herramientas', 'Herramientas y equipos', '#EF4444'),
    ('Oficina', 'Suministros de oficina', '#6366F1'),
    ('Otros', 'Otros productos', '#9CA3AF')
) AS cat(name, description, color_hex)
ON CONFLICT (organization_id, name) DO NOTHING;
