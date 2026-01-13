-- V30: Seed plans data

INSERT INTO plans (code, name, price_monthly, price_yearly, currency_code, max_users, max_projects, feature_flags, is_active)
VALUES
    ('FREE', 'Free Plan', 0.00, 0.00, 'INR', 2, 2, '{"kanban": true, "time_tracking": false, "invoicing": false, "reports": false}', true),
    ('STARTER', 'Starter Plan', 999.00, 9990.00, 'INR', 5, 10, '{"kanban": true, "time_tracking": true, "invoicing": true, "reports": true}', true),
    ('GROWTH', 'Growth Plan', 2999.00, 29990.00, 'INR', 15, 9999, '{"kanban": true, "time_tracking": true, "invoicing": true, "reports": true, "advanced_analytics": true}', true),
    ('AGENCY', 'Agency Plan', 7999.00, 79990.00, 'INR', 50, 9999, '{"kanban": true, "time_tracking": true, "invoicing": true, "reports": true, "advanced_analytics": true, "white_label": true}', true);
