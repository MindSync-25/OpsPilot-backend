-- V18: Add team assignment to tasks

ALTER TABLE tasks
ADD COLUMN team_id UUID;

-- Add foreign key constraint
ALTER TABLE tasks
ADD CONSTRAINT fk_task_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE SET NULL;

-- Add index for team_id
CREATE INDEX idx_tasks_team_id ON tasks(team_id);
