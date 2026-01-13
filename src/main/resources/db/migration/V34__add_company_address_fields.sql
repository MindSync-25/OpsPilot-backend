-- Add address fields to companies table
ALTER TABLE companies
ADD COLUMN address VARCHAR(255),
ADD COLUMN city VARCHAR(100),
ADD COLUMN state VARCHAR(100),
ADD COLUMN zip_code VARCHAR(20),
ADD COLUMN phone VARCHAR(50);
