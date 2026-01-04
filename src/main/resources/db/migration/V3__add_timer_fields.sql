-- Add timer fields to time_entries table
ALTER TABLE time_entries 
ADD COLUMN IF NOT EXISTS start_time TIMESTAMP,
ADD COLUMN IF NOT EXISTS end_time TIMESTAMP,
ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT FALSE;

-- Create index for finding active timers
CREATE INDEX IF NOT EXISTS idx_time_entries_user_active ON time_entries(user_id, is_active) WHERE is_active = TRUE;
