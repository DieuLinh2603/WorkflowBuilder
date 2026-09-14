-- Keep step and connector configuration as native PostgreSQL JSON.
-- The temporary converter also makes upgrades tolerant of legacy blank/invalid values.
CREATE OR REPLACE FUNCTION pg_temp.safe_jsonb(value TEXT)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
BEGIN
    IF value IS NULL OR btrim(value) = '' THEN
        RETURN '{}'::jsonb;
    END IF;
    RETURN value::jsonb;
EXCEPTION WHEN OTHERS THEN
    RETURN '{}'::jsonb;
END;
$$;

ALTER TABLE workflow_steps
    ALTER COLUMN config_json TYPE JSONB USING pg_temp.safe_jsonb(config_json),
    ALTER COLUMN config_json SET DEFAULT '{}'::jsonb,
    ALTER COLUMN config_json SET NOT NULL;

ALTER TABLE data_connectors
    ALTER COLUMN config_json DROP DEFAULT,
    ALTER COLUMN config_json TYPE JSONB USING pg_temp.safe_jsonb(config_json),
    ALTER COLUMN config_json SET DEFAULT '{}'::jsonb;
