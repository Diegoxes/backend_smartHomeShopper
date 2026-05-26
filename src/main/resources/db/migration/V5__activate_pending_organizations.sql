-- Activar organizaciones pendientes y asegurar columna status

ALTER TABLE IF EXISTS organizations ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE organizations SET status = 'ACTIVE' WHERE status IS NULL OR status = 'PENDING';
