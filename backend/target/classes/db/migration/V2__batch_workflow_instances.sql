ALTER TABLE workflow_instances ADD COLUMN batch_id UUID;
ALTER TABLE workflow_instances ADD COLUMN batch_row_number INTEGER;

CREATE INDEX idx_instances_batch ON workflow_instances(batch_id, batch_row_number);
