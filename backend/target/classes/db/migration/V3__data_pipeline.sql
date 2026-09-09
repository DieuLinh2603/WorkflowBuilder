CREATE TABLE data_connectors (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL UNIQUE,
    connector_type VARCHAR(24) NOT NULL,
    config_json TEXT NOT NULL DEFAULT '{}',
    encrypted_credentials TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE data_connector_grants (
    connector_id UUID NOT NULL REFERENCES data_connectors(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (connector_id, user_id)
);

CREATE TABLE data_pipelines (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    owner_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    definition_json TEXT NOT NULL DEFAULT '{}',
    output_schema_json TEXT NOT NULL DEFAULT '[]',
    business_key VARCHAR(255) NOT NULL,
    schedule_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    scheduled_at TIMESTAMP,
    daily_time TIME,
    timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Bangkok',
    next_run_at TIMESTAMP,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE data_pipeline_runs (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES data_pipelines(id) ON DELETE CASCADE,
    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(24) NOT NULL,
    scheduled_for TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    source_count INTEGER NOT NULL DEFAULT 0,
    input_count INTEGER NOT NULL DEFAULT 0,
    output_count INTEGER NOT NULL DEFAULT 0,
    changed_count INTEGER NOT NULL DEFAULT 0,
    error_stage VARCHAR(80),
    error_message VARCHAR(2000),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (pipeline_id, scheduled_for)
);

CREATE TABLE data_pipeline_files (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES data_pipelines(id) ON DELETE CASCADE,
    source_alias VARCHAR(100) NOT NULL,
    version_number INTEGER NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    content_bytes BYTEA NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (pipeline_id, source_alias, version_number)
);

CREATE TABLE data_datasets (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL UNIQUE REFERENCES data_pipelines(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    latest_version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE data_dataset_versions (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES data_datasets(id) ON DELETE CASCADE,
    pipeline_run_id UUID NOT NULL UNIQUE REFERENCES data_pipeline_runs(id),
    version_number INTEGER NOT NULL,
    record_count INTEGER NOT NULL,
    changed_count INTEGER NOT NULL,
    schema_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (dataset_id, version_number)
);

CREATE TABLE data_dataset_records (
    id UUID PRIMARY KEY,
    dataset_version_id UUID NOT NULL REFERENCES data_dataset_versions(id) ON DELETE CASCADE,
    business_key VARCHAR(500) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    change_type VARCHAR(16) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (dataset_version_id, business_key)
);

CREATE TABLE workflow_data_bindings (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES data_datasets(id) ON DELETE CASCADE,
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    trigger_mode VARCHAR(32) NOT NULL DEFAULT 'AUTO_ON_DATASET_SUCCESS',
    filter_json TEXT NOT NULL DEFAULT '[]',
    mapping_json TEXT NOT NULL DEFAULT '{}',
    last_consumed_version INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (dataset_id, workflow_id)
);

CREATE INDEX idx_pipeline_due ON data_pipelines(status, next_run_at);
CREATE INDEX idx_pipeline_runs_status_retry ON data_pipeline_runs(status, next_attempt_at);
CREATE INDEX idx_dataset_records_key ON data_dataset_records(business_key, dataset_version_id);
CREATE INDEX idx_binding_dataset_active ON workflow_data_bindings(dataset_id, active);

CREATE TABLE workflow_batch_records (
    id UUID PRIMARY KEY,
    instance_id UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    row_number INTEGER NOT NULL,
    business_key VARCHAR(500),
    revision INTEGER NOT NULL DEFAULT 1,
    payload_json TEXT NOT NULL,
    checksum VARCHAR(64),
    current_step_id UUID REFERENCES workflow_steps(id),
    status VARCHAR(32) NOT NULL,
    human_action_at TIMESTAMP,
    source_dataset_version_id UUID REFERENCES data_dataset_versions(id),
    state_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(instance_id, row_number, revision)
);
CREATE INDEX idx_batch_record_instance_status ON workflow_batch_records(instance_id, status);
CREATE INDEX idx_batch_record_business_key ON workflow_batch_records(business_key);
