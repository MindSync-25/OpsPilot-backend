-- V28: Add invoice context and audit fields

-- Add invoice-level context fields
ALTER TABLE invoices
    ADD COLUMN billing_period_start DATE NULL,
    ADD COLUMN billing_period_end DATE NULL,
    ADD COLUMN currency_code VARCHAR(10) NOT NULL DEFAULT 'USD',
    ADD COLUMN payment_terms VARCHAR(50) NULL,
    ADD COLUMN sent_at TIMESTAMP NULL,
    ADD COLUMN paid_at TIMESTAMP NULL,
    ADD COLUMN cancelled_at TIMESTAMP NULL;

-- Add invoice item details for time-based items
ALTER TABLE invoice_items
    ADD COLUMN item_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN user_id UUID NULL REFERENCES users(id),
    ADD COLUMN task_id UUID NULL REFERENCES tasks(id),
    ADD COLUMN rate DECIMAL(10,2) NULL,
    ADD COLUMN minutes INTEGER NULL,
    ADD COLUMN source_time_entry_ids TEXT NULL;

-- Create indexes for performance
CREATE INDEX idx_invoice_items_user_id ON invoice_items(user_id);
CREATE INDEX idx_invoice_items_task_id ON invoice_items(task_id);
CREATE INDEX idx_invoices_billing_period ON invoices(billing_period_start, billing_period_end);
