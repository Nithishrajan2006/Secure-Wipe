-- V9: One-time repair of wipe_jobs records left in non-terminal states
-- from previous backend crashes or restarts.
--
-- SAFETY: This migration only updates STATUS / error_message / completed_at.
-- It does NOT delete any records, does NOT alter any table schema,
-- and does NOT perform any disk I/O.
--
-- Going forward, the WipeJobRecoveryService (ApplicationRunner @Order(1)) handles
-- this automatically on every startup, so newly orphaned jobs will never
-- accumulate again after this migration runs.

UPDATE wipe_jobs
SET
    status        = 'FAILED',
    error_message = 'Wipe interrupted: the backend stopped before this operation could complete. No data was wiped. This record is preserved for audit purposes.',
    completed_at  = COALESCE(completed_at, NOW())
WHERE status IN ('PENDING', 'IN_PROGRESS');
