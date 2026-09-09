ALTER TABLE workflow_condition_clauses ADD COLUMN expression_json TEXT;
ALTER TABLE workflow_condition_clauses ALTER COLUMN field_key DROP NOT NULL;
ALTER TABLE workflow_condition_clauses ALTER COLUMN operator DROP NOT NULL;
ALTER TABLE workflow_connections ADD COLUMN priority INTEGER NOT NULL DEFAULT 100;
