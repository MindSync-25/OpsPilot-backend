-- Create timesheets table for tracking weekly timesheet submission and approval
CREATE TABLE IF NOT EXISTS timesheets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    user_id UUID NOT NULL REFERENCES users(id),
    week_start DATE NOT NULL, -- Monday of the week
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- DRAFT, SUBMITTED, APPROVED, REJECTED
    submitted_at TIMESTAMP NULL,
    approved_at TIMESTAMP NULL,
    approved_by UUID NULL REFERENCES users(id),
    rejection_reason TEXT NULL,
    total_minutes INTEGER NOT NULL DEFAULT 0,
    billable_minutes INTEGER NOT NULL DEFAULT 0,
    non_billable_minutes INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT unique_timesheet_per_week UNIQUE(company_id, user_id, week_start)
);

-- Create indexes for better query performance
CREATE INDEX idx_timesheets_company_id ON timesheets(company_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_timesheets_user_id ON timesheets(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_timesheets_status ON timesheets(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_timesheets_week_start ON timesheets(week_start) WHERE deleted_at IS NULL;
CREATE INDEX idx_timesheets_approved_by ON timesheets(approved_by) WHERE deleted_at IS NULL;

-- Add comment for documentation
COMMENT ON TABLE timesheets IS 'Stores weekly timesheet records for approval workflow';
COMMENT ON COLUMN timesheets.week_start IS 'Monday of the week for this timesheet';
COMMENT ON COLUMN timesheets.status IS 'DRAFT, SUBMITTED, APPROVED, or REJECTED';
COMMENT ON COLUMN timesheets.total_minutes IS 'Total logged minutes from time_entries for the week';
COMMENT ON COLUMN timesheets.billable_minutes IS 'Total billable minutes from time_entries';
COMMENT ON COLUMN timesheets.non_billable_minutes IS 'Total non-billable minutes (total - billable)';
