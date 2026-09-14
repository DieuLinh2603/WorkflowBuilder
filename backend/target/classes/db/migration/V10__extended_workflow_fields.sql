ALTER TABLE custom_field_definitions
    ADD COLUMN IF NOT EXISTS configuration_json TEXT NOT NULL DEFAULT '{}';
