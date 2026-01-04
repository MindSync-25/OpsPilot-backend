-- Fix: SUPER_USERs should not be assigned to teams
-- They manage teams, they don't belong to teams

UPDATE users 
SET team_id = NULL 
WHERE role = 'SUPER_USER' 
  AND team_id IS NOT NULL;
