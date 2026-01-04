-- Create leave_requests table for absence/leave tracking
CREATE TABLE IF NOT EXISTS leave_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    user_id UUID NOT NULL REFERENCES users(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    type VARCHAR(30) NOT NULL, -- PTO, SICK, HOLIDAY, UNPAID
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED, CANCELLED
    reason TEXT NULL,
    approver_id UUID NULL REFERENCES users(id),
    decision_note TEXT NULL,
    decided_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

-- Create indexes for better query performance
CREATE INDEX idx_leave_requests_company_id ON leave_requests(company_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_leave_requests_user_id ON leave_requests(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_leave_requests_status ON leave_requests(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_leave_requests_start_date ON leave_requests(start_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_leave_requests_end_date ON leave_requests(end_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_leave_requests_approver_id ON leave_requests(approver_id) WHERE deleted_at IS NULL;

-- Add comments for documentation
COMMENT ON TABLE leave_requests IS 'Stores leave/absence requests for approval workflow';
COMMENT ON COLUMN leave_requests.type IS 'Leave type: PTO, SICK, HOLIDAY, UNPAID';
COMMENT ON COLUMN leave_requests.status IS 'Request status: PENDING, APPROVED, REJECTED, CANCELLED';
COMMENT ON COLUMN leave_requests.reason IS 'Employee provided reason for leave';
COMMENT ON COLUMN leave_requests.decision_note IS 'Approver note for approval or rejection';
