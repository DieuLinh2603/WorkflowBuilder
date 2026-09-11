ALTER TABLE system_action_executions
    ADD COLUMN response_selector_json TEXT NOT NULL DEFAULT '{}';
