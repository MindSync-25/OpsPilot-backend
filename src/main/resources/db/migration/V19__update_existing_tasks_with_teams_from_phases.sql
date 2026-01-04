-- V19: Update existing tasks to inherit team from their assigned phase

-- Update tasks that have a phase assigned but no team
UPDATE tasks t
SET team_id = (
    SELECT p.team_id 
    FROM project_phases p 
    WHERE p.id = t.phase_id 
    AND p.team_id IS NOT NULL
)
WHERE t.phase_id IS NOT NULL 
AND t.team_id IS NULL
AND EXISTS (
    SELECT 1 
    FROM project_phases p 
    WHERE p.id = t.phase_id 
    AND p.team_id IS NOT NULL
);
