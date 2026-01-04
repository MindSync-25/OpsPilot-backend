-- Add project_owner_id to projects table
ALTER TABLE projects ADD COLUMN project_owner_id UUID;

-- Add foreign key constraint
ALTER TABLE projects ADD CONSTRAINT fk_projects_owner 
    FOREIGN KEY (project_owner_id) REFERENCES users(id);

-- Add index for better query performance
CREATE INDEX idx_projects_owner_id ON projects(project_owner_id);
