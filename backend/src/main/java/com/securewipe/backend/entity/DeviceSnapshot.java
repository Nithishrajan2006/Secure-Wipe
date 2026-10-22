package com.securewipe.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wipe_job_id", nullable = false)
    private WipeJob wipeJob;

    @Column(name = "device_model")
    private String deviceModel;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Column(name = "interface_type", length = 50)
    private String interfaceType;

    @Column(name = "total_sectors")
    private Long totalSectors;

    @Column(name = "sector_size")
    private Integer sectorSize;

    @Column(name = "smart_status", length = 50)
    private String smartStatus;

    @Column(name = "snapshot_data", columnDefinition = "JSON")
    private String snapshotData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
