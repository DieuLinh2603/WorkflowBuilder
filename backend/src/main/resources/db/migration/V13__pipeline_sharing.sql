ALTER TABLE data_pipelines
    ADD COLUMN shared_with_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_data_pipelines_shared_with ON data_pipelines(shared_with_id);
