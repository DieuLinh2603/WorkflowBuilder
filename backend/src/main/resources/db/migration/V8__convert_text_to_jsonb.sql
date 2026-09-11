-- V8__convert_text_to_jsonb.sql
-- Convert 13 JSON columns from TEXT to JSONB with GIN indexing

-- 1. workflow_instances.field_snapshot: TEXT -> jsonb
ALTER TABLE workflow_instances ALTER COLUMN field_snapshot DROP DEFAULT;
ALTER TABLE workflow_instances
  ALTER COLUMN field_snapshot TYPE jsonb
  USING (CASE
           WHEN field_snapshot IS NULL OR btrim(field_snapshot) = '' THEN '{}'::jsonb
           ELSE field_snapshot::jsonb
         END);
ALTER TABLE workflow_instances ALTER COLUMN field_snapshot SET DEFAULT '{}'::jsonb;

-- 2. request_drafts.field_snapshot: TEXT -> jsonb
ALTER TABLE request_drafts ALTER COLUMN field_snapshot DROP DEFAULT;
ALTER TABLE request_drafts
  ALTER COLUMN field_snapshot TYPE jsonb
  USING (CASE
           WHEN field_snapshot IS NULL OR btrim(field_snapshot) = '' THEN '{}'::jsonb
           ELSE field_snapshot::jsonb
         END);
ALTER TABLE request_drafts ALTER COLUMN field_snapshot SET DEFAULT '{}'::jsonb;

-- 3. workflow_steps.config_json: TEXT -> jsonb
ALTER TABLE workflow_steps
  ALTER COLUMN config_json TYPE jsonb
  USING (CASE
           WHEN config_json IS NULL OR btrim(config_json) = '' THEN '{}'::jsonb
           ELSE config_json::jsonb
         END);

-- 4. workflow_condition_clauses.expression_json: TEXT -> jsonb
ALTER TABLE workflow_condition_clauses
  ALTER COLUMN expression_json TYPE jsonb
  USING (CASE
           WHEN expression_json IS NULL OR btrim(expression_json) = '' THEN NULL
           ELSE expression_json::jsonb
         END);

-- 5. workflow_batch_records.payload_json: TEXT -> jsonb
ALTER TABLE workflow_batch_records
  ALTER COLUMN payload_json TYPE jsonb
  USING (CASE
           WHEN payload_json IS NULL OR btrim(payload_json) = '' THEN '{}'::jsonb
           ELSE payload_json::jsonb
         END);

-- 6. workflow_batch_records.state_json: TEXT -> jsonb
ALTER TABLE workflow_batch_records
  ALTER COLUMN state_json TYPE jsonb
  USING (CASE
           WHEN state_json IS NULL OR btrim(state_json) = '' THEN '{}'::jsonb
           ELSE state_json::jsonb
         END);

-- 7. data_connectors.config_json: TEXT -> jsonb
ALTER TABLE data_connectors ALTER COLUMN config_json DROP DEFAULT;
ALTER TABLE data_connectors
  ALTER COLUMN config_json TYPE jsonb
  USING (CASE
           WHEN config_json IS NULL OR btrim(config_json) = '' THEN '{}'::jsonb
           ELSE config_json::jsonb
         END);
ALTER TABLE data_connectors ALTER COLUMN config_json SET DEFAULT '{}'::jsonb;

-- 8. data_pipelines.definition_json: TEXT -> jsonb
ALTER TABLE data_pipelines ALTER COLUMN definition_json DROP DEFAULT;
ALTER TABLE data_pipelines
  ALTER COLUMN definition_json TYPE jsonb
  USING (CASE
           WHEN definition_json IS NULL OR btrim(definition_json) = '' THEN '{}'::jsonb
           ELSE definition_json::jsonb
         END);
ALTER TABLE data_pipelines ALTER COLUMN definition_json SET DEFAULT '{}'::jsonb;

-- 9. data_pipelines.output_schema_json: TEXT -> jsonb
ALTER TABLE data_pipelines ALTER COLUMN output_schema_json DROP DEFAULT;
ALTER TABLE data_pipelines
  ALTER COLUMN output_schema_json TYPE jsonb
  USING (CASE
           WHEN output_schema_json IS NULL OR btrim(output_schema_json) = '' THEN '[]'::jsonb
           ELSE output_schema_json::jsonb
         END);
ALTER TABLE data_pipelines ALTER COLUMN output_schema_json SET DEFAULT '[]'::jsonb;

-- 10. data_dataset_versions.schema_json: TEXT -> jsonb
ALTER TABLE data_dataset_versions
  ALTER COLUMN schema_json TYPE jsonb
  USING (CASE
           WHEN schema_json IS NULL OR btrim(schema_json) = '' THEN '[]'::jsonb
           ELSE schema_json::jsonb
         END);

-- 11. data_dataset_records.payload_json: TEXT -> jsonb
ALTER TABLE data_dataset_records
  ALTER COLUMN payload_json TYPE jsonb
  USING (CASE
           WHEN payload_json IS NULL OR btrim(payload_json) = '' THEN '{}'::jsonb
           ELSE payload_json::jsonb
         END);

-- 12. workflow_data_bindings.filter_json: TEXT -> jsonb
ALTER TABLE workflow_data_bindings ALTER COLUMN filter_json DROP DEFAULT;
ALTER TABLE workflow_data_bindings
  ALTER COLUMN filter_json TYPE jsonb
  USING (CASE
           WHEN filter_json IS NULL OR btrim(filter_json) = '' THEN '[]'::jsonb
           ELSE filter_json::jsonb
         END);
ALTER TABLE workflow_data_bindings ALTER COLUMN filter_json SET DEFAULT '[]'::jsonb;

-- 13. workflow_data_bindings.mapping_json: TEXT -> jsonb
ALTER TABLE workflow_data_bindings ALTER COLUMN mapping_json DROP DEFAULT;
ALTER TABLE workflow_data_bindings
  ALTER COLUMN mapping_json TYPE jsonb
  USING (CASE
           WHEN mapping_json IS NULL OR btrim(mapping_json) = '' THEN '{}'::jsonb
           ELSE mapping_json::jsonb
         END);
ALTER TABLE workflow_data_bindings ALTER COLUMN mapping_json SET DEFAULT '{}'::jsonb;

-- GIN Indexes for high-frequency queried JSONB columns
CREATE INDEX idx_instances_field_snapshot_gin ON workflow_instances USING gin (field_snapshot);
CREATE INDEX idx_batch_records_payload_gin ON workflow_batch_records USING gin (payload_json);
CREATE INDEX idx_dataset_records_payload_gin ON data_dataset_records USING gin (payload_json);
