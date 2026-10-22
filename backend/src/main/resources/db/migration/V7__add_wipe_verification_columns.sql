ALTER TABLE wipe_jobs
    ADD COLUMN verification_passed BOOLEAN NULL DEFAULT FALSE,
    ADD COLUMN verification_sample_count INT NULL DEFAULT 0,
    ADD COLUMN verification_mismatch_count INT NULL DEFAULT 0,
    ADD COLUMN verification_mismatch_locations TEXT NULL,
    ADD COLUMN verification_pattern VARCHAR(64) NULL;
