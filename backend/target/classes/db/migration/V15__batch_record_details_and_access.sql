ALTER TABLE workflow_batch_records
    ADD COLUMN last_outcome VARCHAR(40),
    ADD COLUMN last_reason VARCHAR(2000),
    ADD COLUMN last_actor_id UUID REFERENCES users(id),
    ADD COLUMN last_action_at TIMESTAMP,
    ADD COLUMN completed_at TIMESTAMP;

CREATE TABLE workflow_batch_record_access (
    id UUID PRIMARY KEY,
    batch_record_id UUID NOT NULL REFERENCES workflow_batch_records(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (batch_record_id, user_id, access_type)
);

CREATE INDEX idx_batch_record_access_user
    ON workflow_batch_record_access(user_id, batch_record_id);
CREATE INDEX idx_batch_record_instance_row_revision
    ON workflow_batch_records(instance_id, row_number, revision DESC);
