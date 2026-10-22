-- V8: Add audit integrity columns (previous_hash, entry_hash) to audit_logs.
--
-- This migration is written to be safe against partial prior execution.
-- The ALTER TABLE and CREATE UNIQUE INDEX statements are guarded so they
-- only run if the column / index does not already exist.
-- The UPDATE statements are safe to re-run because they only set rows
-- where the value is still NULL.

-- Step 1: Add previous_hash column only if it does not already exist.
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'audit_logs'
      AND COLUMN_NAME  = 'previous_hash'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE audit_logs ADD COLUMN previous_hash VARCHAR(128) NULL AFTER status',
    'SELECT ''previous_hash column already exists, skipping'' AS info'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Step 2: Add entry_hash column only if it does not already exist.
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'audit_logs'
      AND COLUMN_NAME  = 'entry_hash'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE audit_logs ADD COLUMN entry_hash VARCHAR(128) NULL AFTER previous_hash',
    'SELECT ''entry_hash column already exists, skipping'' AS info'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Step 3: Populate previous_hash for rows where it is still NULL.
-- Uses a derived-table subquery to avoid MySQL's restriction on
-- self-referencing the update target in a FROM clause.
UPDATE audit_logs a
JOIN (
    SELECT a2.id,
           (SELECT b.entry_hash
            FROM (SELECT id, entry_hash FROM audit_logs) b
            WHERE b.id < a2.id
            ORDER BY b.id DESC
            LIMIT 1
           ) AS prev_hash
    FROM audit_logs a2
    WHERE a2.previous_hash IS NULL
) src ON a.id = src.id
SET a.previous_hash = src.prev_hash;

-- Step 4: Populate entry_hash for rows where it is still NULL.
UPDATE audit_logs
SET entry_hash = SHA2(
    CONCAT(
        COALESCE(username,     ''), '|',
        COALESCE(action,       ''), '|',
        COALESCE(resource_type,''), '|',
        COALESCE(resource_id,  ''), '|',
        COALESCE(details,      ''), '|',
        COALESCE(ip_address,   ''), '|',
        COALESCE(user_agent,   ''), '|',
        COALESCE(status,       ''), '|',
        COALESCE(DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s'), ''), '|',
        COALESCE(previous_hash,'')
    ),
    256
)
WHERE entry_hash IS NULL;

-- Step 5: Create the unique index only if it does not already exist.
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'audit_logs'
      AND INDEX_NAME   = 'uk_audit_logs_entry_hash'
);
SET @sql = IF(@idx_exists = 0,
    'CREATE UNIQUE INDEX uk_audit_logs_entry_hash ON audit_logs(entry_hash)',
    'SELECT ''uk_audit_logs_entry_hash index already exists, skipping'' AS info'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
