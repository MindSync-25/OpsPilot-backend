-- Remove global unique constraint on users email and add company-scoped unique constraint
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;

-- Add composite unique constraint for email within a company
CREATE UNIQUE INDEX users_email_company_unique ON users(email, company_id) WHERE deleted_at IS NULL;

-- Note: This allows the same email to exist in different companies
-- but prevents duplicate emails within the same company
