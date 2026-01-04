-- Create a test company
INSERT INTO companies (id, name, created_at, updated_at)
VALUES ('550e8400-e29b-41d4-a716-446655440000', 'Test Company Inc.', NOW(), NOW());

-- Create admin user (email: admin@test.com, password: admin123)
INSERT INTO users (id, company_id, name, email, password, role, created_at, updated_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440001',
    '550e8400-e29b-41d4-a716-446655440000',
    'Admin User',
    'admin@test.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMye7jrv/KFa/8K8.QohQCs/zAGQBj5Z.9K',
    'ADMIN',
    NOW(),
    NOW()
);

-- Create member user (email: member@test.com, password: member123)
INSERT INTO users (id, company_id, name, email, password, role, created_at, updated_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440002',
    '550e8400-e29b-41d4-a716-446655440000',
    'Member User',
    'member@test.com',
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
    'MEMBER',
    NOW(),
    NOW()
);
