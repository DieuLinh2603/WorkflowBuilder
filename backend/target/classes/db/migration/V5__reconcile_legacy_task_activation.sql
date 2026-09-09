-- Some development databases applied an older V1 before task activations were
-- folded into the consolidated baseline. Keep this migration idempotent so it
-- is also safe for a fresh database whose V1 already contains the column.
ALTER TABLE workflow_tasks ADD COLUMN IF NOT EXISTS activation_id UUID;
UPDATE workflow_tasks SET activation_id = gen_random_uuid() WHERE activation_id IS NULL;
ALTER TABLE workflow_tasks ALTER COLUMN activation_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tasks_activation
    ON workflow_tasks(instance_id, step_id, activation_id);
