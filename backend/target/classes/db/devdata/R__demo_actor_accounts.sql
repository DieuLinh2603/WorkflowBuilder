-- Local development data. This location is loaded only by application-local.yml.
INSERT INTO users (email, password_hash, display_name, job_title, active, created_at, updated_at)
VALUES
    ('admin@company.com',  '$2a$10$d4L.E0B0odKXelAjffXlZezs92.zv.wBK3cdg1cEtOeBGuzX1fsvC', 'System Admin',   'Administrator',  TRUE, NOW(), NOW()),
    ('owner@company.com',  '$2a$10$2l3Tmqr2pN./i.y5ZkCQledK6uo1TLtU6HkMLPsbbVOEkkyJdfQcC', 'Workflow Owner', 'Workflow Owner', TRUE, NOW(), NOW()),
    ('editor@company.com', '$2a$10$lCgYNgp8e0EF0jf.uFwHEekRACGEfkX6HMEKUGXb04eztIMjTtBXi', 'System Editor',  'Editor',         TRUE, NOW(), NOW()),
    ('viewer@company.com', '$2a$10$2P9SYRX/AUlou1k7nFUSn.3pFFUlVyH/4bP3INo/00bARAFnicNv.', 'System Viewer',  'Viewer',         TRUE, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_system_role (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE email = 'admin@company.com'
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO user_system_role (user_id, role)
SELECT id, 'WORKFLOW_OWNER' FROM users WHERE email = 'owner@company.com'
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO user_system_role (user_id, role)
SELECT id, 'EDITOR' FROM users WHERE email = 'editor@company.com'
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO user_system_role (user_id, role)
SELECT id, 'VIEWER' FROM users WHERE email = 'viewer@company.com'
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO user_module_memberships (user_id, module_code)
SELECT u.id, m.code
FROM users u CROSS JOIN business_modules m
WHERE u.email IN ('owner@company.com', 'editor@company.com', 'viewer@company.com')
ON CONFLICT (user_id, module_code) DO NOTHING;
