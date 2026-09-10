CREATE TABLE workflow_types (
    code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    guidance_json TEXT NOT NULL DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE business_modules (
    code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE user_module_memberships (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    module_code VARCHAR(64) NOT NULL REFERENCES business_modules(code),
    PRIMARY KEY (user_id, module_code)
);

INSERT INTO workflow_types(code, name, description, guidance_json, sort_order) VALUES
('APPROVAL', 'Phê duyệt', 'Quy trình có quyết định phê duyệt hoặc từ chối.', '{"recommendedStepTypes":["APPROVAL","NOTIFICATION","END"],"checklist":["Xác định người phê duyệt","Cấu hình nhánh duyệt và từ chối","Thiết lập deadline và thông báo"]}', 10),
('REVIEW', 'Rà soát', 'Quy trình kiểm tra nội dung và trả kết quả đạt hoặc không đạt.', '{"recommendedStepTypes":["REVIEW","ASSIGNMENT","END"],"checklist":["Xác định tiêu chí rà soát","Cấu hình hướng xử lý khi không đạt","Yêu cầu nhận xét khi cần"]}', 20),
('ASSIGNMENT', 'Phân công xử lý', 'Quy trình giao và theo dõi công việc.', '{"recommendedStepTypes":["ASSIGNMENT","NOTIFICATION","END"],"checklist":["Chọn người hoặc nhóm thực hiện","Chọn cơ chế hoàn thành","Thiết lập deadline"]}', 30),
('AUTOMATION', 'Thông báo / Tự động', 'Quy trình thiên về thông báo và tác vụ hệ thống.', '{"recommendedStepTypes":["SYSTEM_ACTION","NOTIFICATION","END"],"checklist":["Cấu hình endpoint hoặc kênh gửi","Chọn chính sách khi tác vụ lỗi","Kiểm tra dữ liệu đầu vào"]}', 40),
('CUSTOM', 'Tùy chỉnh', 'Quy trình tự thiết kế theo nhu cầu nghiệp vụ.', '{"recommendedStepTypes":[],"checklist":["Xác định đầy đủ các bên tham gia","Thiết kế các nhánh xử lý","Kiểm tra trường hợp ngoại lệ"]}', 50);

INSERT INTO business_modules(code, name, description, sort_order) VALUES
('HR_ADMIN', 'Hành chính - Nhân sự', 'Quy trình hành chính và nhân sự.', 10),
('SALES', 'Kinh doanh', 'Quy trình bán hàng và kinh doanh.', 20),
('FINANCE', 'Kế toán - Tài chính', 'Quy trình kế toán, tài chính và chi phí.', 30),
('IT', 'IT', 'Quy trình công nghệ thông tin.', 40),
('GENERAL', 'Khác', 'Quy trình dùng chung hoặc chưa phân loại.', 50);

INSERT INTO workflow_types(code, name, description, guidance_json, sort_order)
SELECT DISTINCT 'LEGACY_' || UPPER(SUBSTRING(MD5(TRIM(type)), 1, 12)), TRIM(type),
       'Loại workflow được bảo toàn từ dữ liệu cũ.', '{}', 900
FROM workflows
WHERE type IS NOT NULL AND TRIM(type) <> ''
  AND type NOT IN ('Phê duyệt chi phí', 'Phê duyệt mua sắm', 'Phê duyệt hợp đồng', 'Nhân sự', 'Pháp lý', 'Khác')
ON CONFLICT (code) DO NOTHING;

INSERT INTO business_modules(code, name, description, sort_order)
SELECT DISTINCT 'LEGACY_' || UPPER(SUBSTRING(MD5(TRIM(module)), 1, 12)), TRIM(module),
       'Module được bảo toàn từ dữ liệu cũ.', 900
FROM workflows
WHERE module IS NOT NULL AND TRIM(module) <> ''
  AND module NOT IN ('Hành chính - Nhân sự', 'Kinh doanh', 'Kế toán - Tài chính', 'IT', 'Khác')
ON CONFLICT (code) DO NOTHING;

UPDATE workflows SET module = CASE
    WHEN module = 'Hành chính - Nhân sự' THEN 'HR_ADMIN'
    WHEN module = 'Kinh doanh' THEN 'SALES'
    WHEN module = 'Kế toán - Tài chính' THEN 'FINANCE'
    WHEN module = 'IT' THEN 'IT'
    WHEN module = 'Khác' OR module IS NULL OR TRIM(module) = '' THEN 'GENERAL'
    ELSE 'LEGACY_' || UPPER(SUBSTRING(MD5(TRIM(module)), 1, 12))
END;

UPDATE workflows w SET type = CASE
    WHEN EXISTS (SELECT 1 FROM workflow_steps s WHERE s.workflow_id = w.id AND s.type = 'APPROVAL') THEN 'APPROVAL'
    WHEN EXISTS (SELECT 1 FROM workflow_steps s WHERE s.workflow_id = w.id AND s.type = 'REVIEW') THEN 'REVIEW'
    WHEN EXISTS (SELECT 1 FROM workflow_steps s WHERE s.workflow_id = w.id AND s.type = 'ASSIGNMENT') THEN 'ASSIGNMENT'
    WHEN EXISTS (SELECT 1 FROM workflow_steps s WHERE s.workflow_id = w.id AND s.type IN ('SYSTEM_ACTION', 'NOTIFICATION')) THEN 'AUTOMATION'
    WHEN type IN ('Phê duyệt chi phí', 'Phê duyệt mua sắm', 'Phê duyệt hợp đồng') THEN 'APPROVAL'
    WHEN type IN ('Nhân sự', 'Pháp lý', 'Khác') OR type IS NULL OR TRIM(type) = '' THEN 'CUSTOM'
    ELSE 'LEGACY_' || UPPER(SUBSTRING(MD5(TRIM(type)), 1, 12))
END;

WITH family_roots AS (
    SELECT family_id, module, name,
           ROW_NUMBER() OVER (PARTITION BY module, LOWER(name) ORDER BY created_at, id) AS duplicate_number
    FROM workflows
    WHERE version = '1.0'
), duplicate_families AS (
    SELECT family_id, duplicate_number FROM family_roots WHERE duplicate_number > 1
)
UPDATE workflows w
SET name = w.name || ' (' || duplicate_families.duplicate_number || ')'
FROM duplicate_families
WHERE w.family_id = duplicate_families.family_id;

ALTER TABLE workflows ALTER COLUMN type SET NOT NULL;
ALTER TABLE workflows ALTER COLUMN module SET NOT NULL;
ALTER TABLE workflows ADD CONSTRAINT fk_workflow_type FOREIGN KEY (type) REFERENCES workflow_types(code);
ALTER TABLE workflows ADD CONSTRAINT fk_workflow_module FOREIGN KEY (module) REFERENCES business_modules(code);

INSERT INTO user_module_memberships(user_id, module_code)
SELECT u.id, m.code
FROM users u CROSS JOIN business_modules m
WHERE u.active = TRUE
ON CONFLICT DO NOTHING;

CREATE UNIQUE INDEX uq_workflow_root_name_module
    ON workflows(module, LOWER(name)) WHERE version = '1.0';
CREATE INDEX idx_user_module_code ON user_module_memberships(module_code, user_id);
CREATE INDEX idx_workflow_type_module ON workflows(type, module);
