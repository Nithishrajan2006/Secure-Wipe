package com.securewipe.backend.entity;

import com.securewipe.backend.enums.DeviceType;
import com.securewipe.backend.enums.WipeMethod;
import com.securewipe.backend.enums.WipeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wipe_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WipeJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", unique = true, nullable = false, length = 36)
    private String jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "device_path", nullable = false, length = 500)
    private String devicePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    private DeviceType deviceType;

    @Column(name = "device_size")
    @Builder.Default
    private Long deviceSize = 0L;

    @Column(name = "file_system", length = 50)
    private String fileSystem;

    @Column(name = "mount_point")
    private String mountPoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "wipe_method", nullable = false)
    private WipeMethod wipeMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WipeStatus status = WipeStatus.PENDING;

    @Column(name = "progress_percent")
    @Builder.Default
    private Integer progressPercent = 0;

    @Column(name = "bytes_wiped")
    @Builder.Default
    private Long bytesWiped = 0L;

    @Column(name = "wipe_speed_mbps")
    @Builder.Default
    private Double wipeSpeedMbps = 0.0;

    @Column(name = "estimated_time_remaining_seconds")
    @Builder.Default
    private Long estimatedTimeRemainingSeconds = 0L;

    @Column(name = "current_pass")
    @Builder.Default
    private Integer currentPass = 0;

    @Column(name = "total_passes")
    @Builder.Default
    private Integer totalPasses = 1;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "certificate_id", length = 36)
    private String certificateId;

    @Column(name = "verification_passed")
    @Builder.Default
    private Boolean verificationPassed = false;

    @Column(name = "verification_sample_count")
    @Builder.Default
    private Integer verificationSampleCount = 0;

    @Column(name = "verification_mismatch_count")
    @Builder.Default
    private Integer verificationMismatchCount = 0;

    @Column(name = "verification_mismatch_locations", columnDefinition = "TEXT")
    private String verificationMismatchLocations;

    @Column(name = "verification_pattern", length = 64)
    private String verificationPattern;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
