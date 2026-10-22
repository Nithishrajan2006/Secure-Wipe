CREATE TABLE wipe_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id VARCHAR(36) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    device_name VARCHAR(255) NOT NULL,
    device_path VARCHAR(500) NOT NULL,
    device_type ENUM('HDD','SSD','USB','EXTERNAL_HDD','NVME','PARTITION','FILE','FOLDER') NOT NULL,
    device_size BIGINT DEFAULT 0,
    file_system VARCHAR(50),
    mount_point VARCHAR(255),
    wipe_method ENUM('ZEROS','DOD_3PASS','DOD_7PASS','RANDOM','ATA_SECURE_ERASE') NOT NULL,
    status ENUM('PENDING','IN_PROGRESS','COMPLETED','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    progress_percent INT NOT NULL DEFAULT 0,
    bytes_wiped BIGINT NOT NULL DEFAULT 0,
    wipe_speed_mbps DOUBLE NOT NULL DEFAULT 0,
    estimated_time_remaining_seconds BIGINT NOT NULL DEFAULT 0,
    current_pass INT DEFAULT 0,
    total_passes INT DEFAULT 1,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    error_message TEXT NULL,
    certificate_id VARCHAR(36) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wipe_jobs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_wipe_jobs_user_id ON wipe_jobs(user_id);
CREATE INDEX idx_wipe_jobs_status ON wipe_jobs(status);
CREATE INDEX idx_wipe_jobs_created_at ON wipe_jobs(created_at);
CREATE INDEX idx_wipe_jobs_job_id ON wipe_jobs(job_id);
