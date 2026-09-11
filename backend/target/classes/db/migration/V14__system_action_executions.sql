CREATE TABLE system_action_executions (
    id UUID PRIMARY KEY,
    instance_id UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    step_id UUID NOT NULL REFERENCES workflow_steps(id),
    connector_id UUID REFERENCES data_connectors(id) ON DELETE SET NULL,
    batch_row_number INTEGER,
    status VARCHAR(24) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 1,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    http_method VARCHAR(12) NOT NULL,
    request_url VARCHAR(2048) NOT NULL,
    request_headers_json TEXT NOT NULL DEFAULT '{}',
    request_body TEXT,
    response_mappings_json TEXT NOT NULL DEFAULT '[]',
    response_status INTEGER,
    response_body TEXT,
    mapped_outputs_json TEXT NOT NULL DEFAULT '{}',
    error_message VARCHAR(2000),
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    resumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_system_action_due ON system_action_executions(status, next_attempt_at);
CREATE INDEX idx_system_action_instance ON system_action_executions(instance_id, created_at);
