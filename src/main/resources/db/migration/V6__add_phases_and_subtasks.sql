-- V3: Add Project Phases and Subtasks with hierarchy: Project -> Phases -> Tasks -> Subtasks
-- Migration for Phase layer introduction

-- =====================================================
-- 1) Create project_phases table
-- =====================================================
CREATE TABLE project_phases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    project_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, COMPLETED, ARCHIVED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    
    CONSTRAINT fk_phase_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_phase_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Indexes for project_phases
CREATE INDEX idx_project_phases_company_id ON project_phases(company_id);
CREATE INDEX idx_project_phases_project_id ON project_phases(project_id);
CREATE INDEX idx_project_phases_project_sort ON project_phases(project_id, sort_order);
CREATE INDEX idx_project_phases_deleted_at ON project_phases(deleted_at);

-- =====================================================
-- 2) Add phase_id to tasks table (nullable for backward compatibility)
-- =====================================================
ALTER TABLE tasks 
ADD COLUMN phase_id UUID,
ADD CONSTRAINT fk_task_phase FOREIGN KEY (phase_id) REFERENCES project_phases(id) ON DELETE SET NULL;

-- Index for phase_id in tasks
CREATE INDEX idx_tasks_phase_id ON tasks(phase_id);

-- =====================================================
-- 3) Create subtasks table
-- =====================================================
CREATE TABLE subtasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    task_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'TODO', -- TODO, IN_PROGRESS, DONE
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    
    CONSTRAINT fk_subtask_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_subtask_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

-- Indexes for subtasks
CREATE INDEX idx_subtasks_company_id ON subtasks(company_id);
CREATE INDEX idx_subtasks_task_id ON subtasks(task_id);
CREATE INDEX idx_subtasks_task_sort ON subtasks(task_id, sort_order);
CREATE INDEX idx_subtasks_deleted_at ON subtasks(deleted_at);

-- =====================================================
-- Trigger for updated_at on project_phases
-- =====================================================
CREATE OR REPLACE FUNCTION update_project_phases_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_project_phases_updated_at
    BEFORE UPDATE ON project_phases
    FOR EACH ROW
    EXECUTE FUNCTION update_project_phases_updated_at();

-- =====================================================
-- Trigger for updated_at on subtasks
-- =====================================================
CREATE OR REPLACE FUNCTION update_subtasks_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_subtasks_updated_at
    BEFORE UPDATE ON subtasks
    FOR EACH ROW
    EXECUTE FUNCTION update_subtasks_updated_at();
