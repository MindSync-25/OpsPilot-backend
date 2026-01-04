-- Add created_by field to invoices table
ALTER TABLE invoices ADD COLUMN created_by UUID;
