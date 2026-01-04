-- V5__make_client_id_optional_in_projects.sql
-- Make client_id column nullable in projects table

ALTER TABLE projects 
ALTER COLUMN client_id DROP NOT NULL;
