-- V4: Teams and Roles Enhancement
-- Create teams table
CREATE TABLE teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    name VARCHAR(150) NOT NULL,
    lead_user_id UUID REFERENCES users(id),
    created_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT unique_company_team_name UNIQUE(company_id, name)
);

CREATE INDEX idx_teams_company_id ON teams(company_id);
CREATE INDEX idx_teams_lead_user_id ON teams(lead_user_id);
CREATE INDEX idx_teams_created_by_user_id ON teams(created_by_user_id);

-- Update users table with team and hierarchy fields
ALTER TABLE users
ADD COLUMN team_id UUID REFERENCES teams(id),
ADD COLUMN designation VARCHAR(150),
ADD COLUMN created_by_user_id UUID REFERENCES users(id),
ADD COLUMN manager_user_id UUID REFERENCES users(id);

CREATE INDEX idx_users_team_id ON users(team_id);
CREATE INDEX idx_users_created_by_user_id ON users(created_by_user_id);
CREATE INDEX idx_users_manager_user_id ON users(manager_user_id);
