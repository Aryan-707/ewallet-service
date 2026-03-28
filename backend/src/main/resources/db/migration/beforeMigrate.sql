-- Flyway "beforeMigrate" callback.
-- Removes any FAILED migration entry for V8 so Flyway will retry it
-- after the SQL bug (SELECT → PERFORM) was fixed.
-- This is idempotent: if no failed row exists the DELETE is a no-op.
DELETE FROM flyway_schema_history
WHERE version = '8'
  AND success = false;
