CREATE TABLE device_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wipe_job_id BIGINT NOT NULL,
    device_model VARCHAR(255),
    serial_number VARCHAR(100),
    firmware_version VARCHAR(50),
    interface_type VARCHAR(50),
    total_sectors BIGINT,
    sector_size INT,
    smart_status VARCHAR(50),
    snapshot_data JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_snapshots_wipe_job FOREIGN KEY (wipe_job_id) REFERENCES wipe_jobs(id) ON DELETE CASCADE
);

CREATE INDEX idx_device_snapshots_wipe_job_id ON device_snapshots(wipe_job_id);
