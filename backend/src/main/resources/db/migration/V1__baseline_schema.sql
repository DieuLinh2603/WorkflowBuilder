-- Consolidated development baseline.
-- This file represents the complete schema after the former V1-V14 migrations.

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    job_title VARCHAR(255),
    manager_id UUID REFERENCES users(id) ON DELETE SET NULL,
    data_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    auth_version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE user_system_role (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE password_reset_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(255),
    module VARCHAR(255),
    owner_id UUID NOT NULL REFERENCES users(id),
    version VARCHAR(255) NOT NULL DEFAULT '1.0',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    family_id UUID NOT NULL,
    source_workflow_id UUID REFERENCES workflows(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE workflow_editors (
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (workflow_id, user_id)
);

CREATE TABLE user_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE group_members (
    group_id UUID NOT NULL REFERENCES user_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE workflow_audiences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    subject_type VARCHAR(32) NOT NULL,
    subject_value VARCHAR(255) NOT NULL,
    UNIQUE (workflow_id, subject_type, subject_value)
);

CREATE TABLE workflow_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    label VARCHAR(255),
    position_x INTEGER,
    position_y INTEGER,
    config_json TEXT
);

CREATE TABLE custom_field_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    step_id UUID NOT NULL REFERENCES workflow_steps(id) ON DELETE CASCADE,
    field_key VARCHAR(255) NOT NULL,
    label VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    placeholder VARCHAR(255),
    display_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE (step_id, field_key)
);

CREATE TABLE workflow_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    from_step_id UUID NOT NULL REFERENCES workflow_steps(id),
    to_step_id UUID NOT NULL REFERENCES workflow_steps(id),
    type VARCHAR(32) NOT NULL DEFAULT 'DEFAULT',
    logical_operator VARCHAR(8) NOT NULL DEFAULT 'AND'
);

CREATE TABLE workflow_condition_clauses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id UUID NOT NULL REFERENCES workflow_connections(id) ON DELETE CASCADE,
    field_key VARCHAR(255) NOT NULL,
    operator VARCHAR(32) NOT NULL,
    expected_value TEXT,
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE workflow_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    request_code VARCHAR(64) NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES users(id),
    current_step_id UUID REFERENCES workflow_steps(id),
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    field_snapshot TEXT NOT NULL DEFAULT '{}',
    condition_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    withdrawn_by UUID REFERENCES users(id),
    withdrawn_at TIMESTAMP,
    withdrawal_reason TEXT,
    requester_withdrawal_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE workflow_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    step_id UUID NOT NULL REFERENCES workflow_steps(id),
    assignee_id UUID NOT NULL REFERENCES users(id),
    activation_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    deadline_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE instance_step_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    step_id UUID NOT NULL REFERENCES workflow_steps(id),
    actor_id UUID REFERENCES users(id),
    action VARCHAR(64) NOT NULL,
    comment TEXT,
    acted_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    request_code VARCHAR(64),
    link_url VARCHAR(500),
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE reminder_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES workflow_tasks(id) ON DELETE CASCADE,
    channel VARCHAR(32) NOT NULL,
    deadline_at TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message VARCHAR(1000),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (task_id, channel, deadline_at)
);

CREATE TABLE request_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workflow_family_id UUID NOT NULL,
    workflow_version_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    field_snapshot TEXT NOT NULL DEFAULT '{}',
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, workflow_family_id)
);

CREATE TABLE system_settings (
    setting_key VARCHAR(255) PRIMARY KEY,
    setting_value VARCHAR(1000) NOT NULL
);

CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

INSERT INTO system_settings (setting_key, setting_value)
VALUES ('deadlineReminderLeadTimeHours', '24');

CREATE INDEX idx_users_manager ON users(manager_id);
CREATE INDEX idx_users_active ON users(active);
CREATE INDEX idx_password_reset_token_user ON password_reset_token(user_id);
CREATE INDEX idx_password_reset_token_expiry ON password_reset_token(expires_at);
CREATE INDEX idx_workflows_family_status ON workflows(family_id, status);
CREATE INDEX idx_instances_creator ON workflow_instances(created_by, started_at DESC);
CREATE INDEX idx_tasks_assignee_status ON workflow_tasks(assignee_id, status);
CREATE INDEX idx_tasks_activation ON workflow_tasks(instance_id, step_id, activation_id);
CREATE INDEX idx_tasks_deadline ON workflow_tasks(deadline_at) WHERE status = 'PENDING';
CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, created_at DESC);
CREATE INDEX idx_request_drafts_user_updated ON request_drafts(user_id, updated_at DESC);
