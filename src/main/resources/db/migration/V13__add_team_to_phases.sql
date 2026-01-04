-- V13: Add team assignment to project phases

ALTER TABLE project_phases
ADD COLUMN team_id UUID;

-- Add foreign key constraint
ALTER TABLE project_phases
ADD CONSTRAINT fk_phase_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE SET NULL;

-- Add index for team_id
CREATE INDEX idx_project_phases_team_id ON project_phases(team_id);
