-- Update test company subscription to AGENCY plan
UPDATE subscriptions 
SET plan_code = 'AGENCY' 
WHERE company_id = (SELECT id FROM companies WHERE name = 'test' LIMIT 1);
