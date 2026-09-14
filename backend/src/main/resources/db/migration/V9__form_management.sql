-- V9__form_management.sql
-- Standalone Form Builder: forms, form_versions, form_fields, step_forms, and instance reference

-- 1. Gia đình form (metadata)
CREATE TABLE forms (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    created_by  UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- 2. Các version của form
CREATE TABLE form_versions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_id      UUID NOT NULL REFERENCES forms(id) ON DELETE CASCADE,
    version      INTEGER NOT NULL DEFAULT 1,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP,
    published_by UUID REFERENCES users(id),
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (form_id, version)
);

-- 3. Fields trong từng version
CREATE TABLE form_fields (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_version_id UUID NOT NULL REFERENCES form_versions(id) ON DELETE CASCADE,
    field_key       VARCHAR(255) NOT NULL,
    label           VARCHAR(255) NOT NULL,
    type            VARCHAR(32)  NOT NULL,
    required        BOOLEAN NOT NULL DEFAULT FALSE,
    placeholder     VARCHAR(255),
    options_json    jsonb,
    validation_json jsonb,
    display_order   INTEGER NOT NULL DEFAULT 0,
    UNIQUE (form_version_id, field_key)
);

-- 4. Junction table gắn form_version vào workflow_step (M:N)
CREATE TABLE step_forms (
    step_id         UUID NOT NULL REFERENCES workflow_steps(id) ON DELETE CASCADE,
    form_version_id UUID NOT NULL REFERENCES form_versions(id) ON DELETE CASCADE,
    PRIMARY KEY (step_id, form_version_id)
);

-- 5. Thêm cột version reference vào workflow_instances
ALTER TABLE workflow_instances
    ADD COLUMN form_version_id UUID REFERENCES form_versions(id);

-- 6. Indexes
CREATE INDEX idx_form_versions_form_id    ON form_versions(form_id);
CREATE INDEX idx_form_fields_version_id   ON form_fields(form_version_id);
CREATE INDEX idx_step_forms_step_id       ON step_forms(step_id);
CREATE INDEX idx_step_forms_form_version  ON step_forms(form_version_id);
CREATE INDEX idx_form_fields_options      ON form_fields USING GIN (options_json);
CREATE INDEX idx_form_fields_validation   ON form_fields USING GIN (validation_json);
