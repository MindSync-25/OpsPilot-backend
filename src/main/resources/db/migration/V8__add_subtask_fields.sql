-- Add new fields to subtasks table to match task fields
ALTER TABLE subtasks ADD COLUMN description TEXT;
ALTER TABLE subtasks ADD COLUMN priority VARCHAR(50) DEFAULT 'MEDIUM';
ALTER TABLE subtasks ADD COLUMN assignee_user_id UUID;
ALTER TABLE subtasks ADD COLUMN due_date DATE;

-- Add foreign key for assignee
ALTER TABLE subtasks ADD CONSTRAINT fk_subtasks_assignee
    FOREIGN KEY (assignee_user_id) REFERENCES users(id) ON DELETE SET NULL;

-- Add index for assignee lookups
CREATE INDEX idx_subtasks_assignee ON subtasks(assignee_user_id);
