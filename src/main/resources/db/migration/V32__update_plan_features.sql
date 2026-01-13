-- V31: Update plan feature flags
-- FREE: Kanban only
-- STARTER: Kanban + Reports
-- GROWTH: Kanban + Reports + Time Tracking + Invoices + Advanced Analytics
-- AGENCY: All GROWTH features + White Label

UPDATE plans SET feature_flags = '{"kanban": true, "time_tracking": false, "invoicing": false, "reports": false}'
WHERE code = 'FREE';

UPDATE plans SET feature_flags = '{"kanban": true, "time_tracking": false, "invoicing": false, "reports": true}'
WHERE code = 'STARTER';

UPDATE plans SET feature_flags = '{"kanban": true, "time_tracking": true, "invoicing": true, "reports": true, "advanced_analytics": true}'
WHERE code = 'GROWTH';

UPDATE plans SET feature_flags = '{"kanban": true, "time_tracking": true, "invoicing": true, "reports": true, "advanced_analytics": true, "white_label": true}'
WHERE code = 'AGENCY';
