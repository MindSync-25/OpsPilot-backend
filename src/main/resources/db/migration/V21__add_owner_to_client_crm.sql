-- Add owner_id and next_follow_up to client_crm table
ALTER TABLE client_crm ADD COLUMN IF NOT EXISTS owner_id UUID;
ALTER TABLE client_crm ADD COLUMN IF NOT EXISTS next_follow_up TIMESTAMP;

-- Add foreign key constraint to users table
ALTER TABLE client_crm ADD CONSTRAINT fk_client_crm_owner 
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL;

-- Add index for better query performance
CREATE INDEX IF NOT EXISTS idx_client_crm_owner_id ON client_crm(owner_id);
