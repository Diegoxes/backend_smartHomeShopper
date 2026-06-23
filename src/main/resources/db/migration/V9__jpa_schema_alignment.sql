-- Alinear esquema legacy con entidades JPA (validate al arrancar).

-- purchases.note (V0 tenía "notes" por error)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'purchases' AND column_name = 'notes'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'purchases' AND column_name = 'note'
    ) THEN
        ALTER TABLE purchases RENAME COLUMN notes TO note;
    END IF;
END $$;

ALTER TABLE purchases ADD COLUMN IF NOT EXISTS note VARCHAR(500);

-- warehouses.created_at (V1 no la creaba)
ALTER TABLE warehouses ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
