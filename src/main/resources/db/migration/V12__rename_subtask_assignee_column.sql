-- Rename assignee_user_id to assigned_to in subtasks table
ALTER TABLE subtasks RENAME COLUMN assignee_user_id TO assigned_to;
