-- Add hourly_rate column to users table for invoice generation
ALTER TABLE users ADD COLUMN hourly_rate DECIMAL(10,2);

-- Add index for performance when checking rates
CREATE INDEX idx_users_hourly_rate ON users(hourly_rate) WHERE hourly_rate IS NOT NULL;

-- Add comment
COMMENT ON COLUMN users.hourly_rate IS 'Hourly billing rate for time-based invoice generation';
