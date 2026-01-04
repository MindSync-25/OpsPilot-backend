-- Add story_points, created_by, and parent_subtask_id to subtasks table

ALTER TABLE subtasks
ADD COLUMN story_points VARCHAR(10),
ADD COLUMN created_by UUID,
ADD COLUMN parent_subtask_id UUID,
ADD CONSTRAINT fk_subtasks_created_by FOREIGN KEY (created_by) REFERENCES users(id),
ADD CONSTRAINT fk_subtasks_parent FOREIGN KEY (parent_subtask_id) REFERENCES subtasks(id) ON DELETE CASCADE;

-- Create indexes for lookups
CREATE INDEX idx_subtasks_created_by ON subtasks(created_by);
CREATE INDEX idx_subtasks_parent ON subtasks(parent_subtask_id);
