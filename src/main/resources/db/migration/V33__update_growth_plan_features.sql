-- V33: Remove advanced_analytics from GROWTH plan
-- Advanced Analytics is now exclusive to AGENCY plan only

UPDATE plans SET feature_flags = '{"kanban": true, "time_tracking": true, "invoicing": true, "reports": true}'
WHERE code = 'GROWTH';
