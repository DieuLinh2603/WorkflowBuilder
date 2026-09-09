# Database migrations

The project is still in development, so the schema history has been consolidated into one baseline:

- `V1__baseline_schema.sql` contains the complete current production schema.
- Local demo accounts live in `db/devdata/R__demo_actor_accounts.sql` and are not loaded by the production profile.
- After changing this baseline, recreate every development database that used the old V1-V14 history.

Once a shared staging or production database exists, this baseline becomes immutable. Every later schema change must
use a new timestamp version, for example `V20260903_1530__add_request_audit_columns.sql`. Do not edit, rename, reorder,
or delete an applied production migration.

`baseline-on-migrate` and `out-of-order` remain disabled by default. Every schema change must pass
`FlywayMigrationTest` against an empty PostgreSQL database.
