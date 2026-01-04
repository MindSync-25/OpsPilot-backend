-- V16__clients_standard.sql
-- Update Clients table to match platform conventions
-- Change contact_person to contact_name, add status field values, ensure proper indexes

-- Rename contact_person to contact_name if it exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'clients' AND column_name = 'contact_person'
    ) THEN
        ALTER TABLE clients RENAME COLUMN contact_person TO contact_name;
    END IF;
END $$;

-- Add contact_name column if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'clients' AND column_name = 'contact_name'
    ) THEN
        ALTER TABLE clients ADD COLUMN contact_name VARCHAR(255);
    END IF;
END $$;

-- Make email nullable (not unique globally, can be null)
ALTER TABLE clients ALTER COLUMN email DROP NOT NULL;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'clients_email_key' AND table_name = 'clients'
    ) THEN
        ALTER TABLE clients DROP CONSTRAINT clients_email_key;
    END IF;
END $$;

-- Ensure status column exists with proper values
-- Status values: ACTIVE, INACTIVE, PROSPECT
ALTER TABLE clients ALTER COLUMN status SET DEFAULT 'ACTIVE';

-- Create indexes for performance (IF NOT EXISTS)
CREATE INDEX IF NOT EXISTS idx_clients_company_id ON clients(company_id);
CREATE INDEX IF NOT EXISTS idx_clients_email ON clients(email);
CREATE INDEX IF NOT EXISTS idx_clients_status ON clients(status);
CREATE INDEX IF NOT EXISTS idx_clients_deleted_at ON clients(deleted_at);

-- Ensure projects.client_id FK is correct
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'projects' AND column_name = 'client_id'
    ) THEN
        -- Drop existing FK constraint if exists
        ALTER TABLE projects DROP CONSTRAINT IF EXISTS fk_projects_client;
        ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_client_id_fkey;
        
        -- Add proper FK constraint with SET NULL on delete
        ALTER TABLE projects ADD CONSTRAINT fk_projects_client 
            FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Ensure invoices.client_id FK is correct
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'invoices' AND column_name = 'client_id'
    ) THEN
        -- Drop existing FK constraint if exists
        ALTER TABLE invoices DROP CONSTRAINT IF EXISTS fk_invoices_client;
        ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_client_id_fkey;
        
        -- Add proper FK constraint with SET NULL on delete
        ALTER TABLE invoices ADD CONSTRAINT fk_invoices_client 
            FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL;
    END IF;
END $$;
