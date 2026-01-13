-- Add sort_order column to plans table
ALTER TABLE plans ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

-- Set sort order values for existing plans
UPDATE plans SET sort_order = 1 WHERE code = 'FREE';
UPDATE plans SET sort_order = 2 WHERE code = 'STARTER';
UPDATE plans SET sort_order = 3 WHERE code = 'GROWTH';
UPDATE plans SET sort_order = 4 WHERE code = 'AGENCY';

-- Create index on sort_order for better query performance
CREATE INDEX idx_plans_sort_order ON plans(sort_order);
