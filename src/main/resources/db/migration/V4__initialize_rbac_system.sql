-- Inicializar sistema RBAC completo
-- Esta migración crea las tablas de roles, módulos y permisos, y los datos iniciales

-- 1. Crear tabla de roles
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE
);

-- 2. Crear tabla de módulos
CREATE TABLE IF NOT EXISTS modules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    key VARCHAR(64) NOT NULL UNIQUE
);

-- 3. Crear tabla de relación roles-módulos (permisos)
CREATE TABLE IF NOT EXISTS role_modules (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    module_id BIGINT NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    can_create BOOLEAN NOT NULL DEFAULT false,
    can_read BOOLEAN NOT NULL DEFAULT false,
    can_update BOOLEAN NOT NULL DEFAULT false,
    can_delete BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uk_role_module_pair UNIQUE (role_id, module_id)
);

CREATE INDEX IF NOT EXISTS idx_role_modules_role ON role_modules(role_id);
CREATE INDEX IF NOT EXISTS idx_role_modules_module ON role_modules(module_id);

-- 4. Insertar roles básicos (si no existen)
INSERT INTO roles (id, name) VALUES (1, 'PLATFORM_OWNER') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (id, name) VALUES (2, 'MANAGER') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (id, name) VALUES (3, 'MEMBER') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (id, name) VALUES (4, 'VIEWER') ON CONFLICT (name) DO NOTHING;

-- Resetear secuencia de roles para evitar conflictos
SELECT setval('roles_id_seq', (SELECT COALESCE(MAX(id), 0) + 1 FROM roles), false);

-- 5. Insertar módulos (si no existen)
INSERT INTO modules (id, name, key) VALUES (1, 'Inventario', 'INVENTORY') ON CONFLICT (key) DO NOTHING;
INSERT INTO modules (id, name, key) VALUES (2, 'Compras', 'PURCHASES') ON CONFLICT (key) DO NOTHING;
INSERT INTO modules (id, name, key) VALUES (3, 'Informes', 'REPORTS') ON CONFLICT (key) DO NOTHING;
INSERT INTO modules (id, name, key) VALUES (4, 'Usuarios', 'USERS') ON CONFLICT (key) DO NOTHING;

-- Resetear secuencia de módulos
SELECT setval('modules_id_seq', (SELECT COALESCE(MAX(id), 0) + 1 FROM modules), false);

-- 6. Configurar permisos para PLATFORM_OWNER (rol_id=1) - CRUD en todos los módulos
INSERT INTO role_modules (role_id, module_id, can_create, can_read, can_update, can_delete) 
VALUES 
    (1, 1, true, true, true, true),  -- INVENTORY
    (1, 2, true, true, true, true),  -- PURCHASES
    (1, 3, true, true, true, true),  -- REPORTS
    (1, 4, true, true, true, true)   -- USERS
ON CONFLICT (role_id, module_id) DO UPDATE SET
    can_create = EXCLUDED.can_create,
    can_read = EXCLUDED.can_read,
    can_update = EXCLUDED.can_update,
    can_delete = EXCLUDED.can_delete;

-- 7. Configurar permisos para MANAGER (rol_id=2) - CRUD salvo eliminar usuarios
INSERT INTO role_modules (role_id, module_id, can_create, can_read, can_update, can_delete) 
VALUES 
    (2, 1, true, true, true, true),   -- INVENTORY: full CRUD
    (2, 2, true, true, true, true),   -- PURCHASES: full CRUD
    (2, 3, true, true, true, true),   -- REPORTS: full CRUD
    (2, 4, true, true, true, false)   -- USERS: CRU (no delete)
ON CONFLICT (role_id, module_id) DO UPDATE SET
    can_create = EXCLUDED.can_create,
    can_read = EXCLUDED.can_read,
    can_update = EXCLUDED.can_update,
    can_delete = EXCLUDED.can_delete;

-- 8. Configurar permisos para MEMBER (rol_id=3) - CRUD inventario, solo lectura resto
INSERT INTO role_modules (role_id, module_id, can_create, can_read, can_update, can_delete) 
VALUES 
    (3, 1, true, true, true, true),     -- INVENTORY: full CRUD
    (3, 2, false, true, false, false),  -- PURCHASES: solo lectura
    (3, 3, false, true, false, false),  -- REPORTS: solo lectura
    (3, 4, false, true, false, false)   -- USERS: solo lectura
ON CONFLICT (role_id, module_id) DO UPDATE SET
    can_create = EXCLUDED.can_create,
    can_read = EXCLUDED.can_read,
    can_update = EXCLUDED.can_update,
    can_delete = EXCLUDED.can_delete;

-- 9. Configurar permisos para VIEWER (rol_id=4) - Solo lectura en todo
INSERT INTO role_modules (role_id, module_id, can_create, can_read, can_update, can_delete) 
VALUES 
    (4, 1, false, true, false, false),  -- INVENTORY: solo lectura
    (4, 2, false, true, false, false),  -- PURCHASES: solo lectura
    (4, 3, false, true, false, false),  -- REPORTS: solo lectura
    (4, 4, false, true, false, false)   -- USERS: solo lectura
ON CONFLICT (role_id, module_id) DO UPDATE SET
    can_create = EXCLUDED.can_create,
    can_read = EXCLUDED.can_read,
    can_update = EXCLUDED.can_update,
    can_delete = EXCLUDED.can_delete;

-- 10. Agregar columna role_id a users si no existe
ALTER TABLE IF EXISTS users ADD COLUMN IF NOT EXISTS role_id BIGINT REFERENCES roles(id);

-- 11. Asignar rol MEMBER a usuarios que no tienen role_id
UPDATE users 
SET role_id = 3  -- MEMBER
WHERE role_id IS NULL;

-- 12. Crear índice útil
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role_id);

-- 13. Log de inicialización completada
COMMENT ON TABLE roles IS 'Sistema RBAC inicializado en V4 - Roles: PLATFORM_OWNER, MANAGER, MEMBER, VIEWER';
