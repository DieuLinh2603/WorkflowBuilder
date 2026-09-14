CREATE TABLE forms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_by UUID NOT NULL REFERENCES users(id),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE form_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_id UUID NOT NULL REFERENCES forms(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    instruction TEXT,
    submission_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE',
    record_recipient_field_key VARCHAR(255),
    max_batch_rows INTEGER NOT NULL DEFAULT 500,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (form_id, version_number)
);

CREATE UNIQUE INDEX uq_form_single_draft ON form_versions(form_id) WHERE status = 'DRAFT';

CREATE TABLE form_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_version_id UUID NOT NULL REFERENCES form_versions(id) ON DELETE CASCADE,
    field_key VARCHAR(255) NOT NULL,
    label VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    placeholder VARCHAR(255),
    configuration_json TEXT NOT NULL DEFAULT '{}',
    display_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE (form_version_id, field_key)
);

ALTER TABLE workflows ADD COLUMN form_version_id UUID REFERENCES form_versions(id);
ALTER TABLE workflow_instances ADD COLUMN form_version_id UUID REFERENCES form_versions(id);
ALTER TABLE request_drafts ADD COLUMN form_version_id UUID REFERENCES form_versions(id);

-- Preserve every existing workflow definition as an independently versioned form family.
INSERT INTO forms (id, name, description, created_by, archived, created_at, updated_at)
SELECT w.family_id, MAX(w.name) || ' - Request Form', MAX(w.description),
       (ARRAY_AGG(w.owner_id ORDER BY w.created_at))[1], FALSE, MIN(w.created_at), MAX(w.updated_at)
FROM workflows w
GROUP BY w.family_id;

INSERT INTO form_versions (id, form_id, version_number, status, instruction, submission_mode,
                           record_recipient_field_key, max_batch_rows, published_at, created_at, updated_at)
SELECT w.id, w.family_id,
       ROW_NUMBER() OVER (PARTITION BY w.family_id ORDER BY w.created_at, w.id)::INTEGER,
       CASE WHEN w.status = 'DRAFT' THEN 'DRAFT' ELSE 'PUBLISHED' END,
       s.config_json::jsonb ->> 'instructionForCreator',
       CASE WHEN s.config_json::jsonb ->> 'submissionMode' = 'BATCH' THEN 'BATCH' ELSE 'SINGLE' END,
       s.config_json::jsonb ->> 'recordRecipientFieldKey',
       COALESCE((s.config_json::jsonb ->> 'maxBatchRows')::INTEGER, 500),
       CASE WHEN w.status = 'DRAFT' THEN NULL ELSE w.updated_at END,
       w.created_at, w.updated_at
FROM workflows w
JOIN workflow_steps s ON s.workflow_id = w.id AND s.type = 'START';

INSERT INTO form_fields (id, form_version_id, field_key, label, type, required, placeholder,
                         configuration_json, display_order)
SELECT c.id, s.workflow_id, c.field_key, c.label, c.type, c.required, c.placeholder,
       COALESCE(c.configuration_json, '{}'), c.display_order
FROM custom_field_definitions c
JOIN workflow_steps s ON s.id = c.step_id
WHERE s.type = 'START';

UPDATE workflows SET form_version_id = id;
UPDATE workflow_instances i SET form_version_id = w.form_version_id FROM workflows w WHERE w.id = i.workflow_id;
UPDATE request_drafts d SET form_version_id = w.form_version_id FROM workflows w WHERE w.id = d.workflow_version_id;

ALTER TABLE workflow_instances ALTER COLUMN form_version_id SET NOT NULL;
ALTER TABLE request_drafts ALTER COLUMN form_version_id SET NOT NULL;

CREATE INDEX idx_workflows_form_version ON workflows(form_version_id);
CREATE INDEX idx_tickets_form_version ON workflow_instances(form_version_id);
CREATE INDEX idx_form_versions_form ON form_versions(form_id, version_number DESC);
CREATE INDEX idx_form_fields_version ON form_fields(form_version_id, display_order);
