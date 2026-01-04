-- Add story_points and created_by to tasks table

ALTER TABLE tasks
ADD COLUMN story_points VARCHAR(10),
ADD COLUMN created_by UUID,
ADD CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by) REFERENCES users(id);

-- Create index for created_by lookups
CREATE INDEX idx_tasks_created_by ON tasks(created_by);
