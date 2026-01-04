-- V26: Add invoice linkage to time_entries for automated billing
-- Allows tracking which time entries have been billed in which invoice

ALTER TABLE time_entries
ADD COLUMN invoice_id UUID REFERENCES invoices(id),
ADD COLUMN billed_at TIMESTAMP;

-- Index for finding unbilled entries
CREATE INDEX idx_time_entries_invoice_id ON time_entries(invoice_id);

-- Composite index for efficient unbilled queries
CREATE INDEX idx_time_entries_unbilled_billable 
ON time_entries(company_id, project_id, date) 
WHERE invoice_id IS NULL AND is_billable = true AND deleted_at IS NULL;

-- Index for date range queries
CREATE INDEX idx_time_entries_date_range 
ON time_entries(company_id, date, invoice_id) 
WHERE deleted_at IS NULL;

COMMENT ON COLUMN time_entries.invoice_id IS 'References the invoice this time entry was billed in';
COMMENT ON COLUMN time_entries.billed_at IS 'Timestamp when this entry was included in an invoice';
