package com.securewipe.backend.dto.response;

import com.securewipe.backend.enums.DeviceType;
import com.securewipe.backend.enums.WipeMethod;
import com.securewipe.backend.enums.WipeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WipeJobDto {
    private Long id;
    private String jobId;
    private Long userId;
    private String username;
    private String deviceName;
    private String devicePath;
    private DeviceType deviceType;
    private Long deviceSize;
    private String deviceSizeFormatted;
    private String fileSystem;
    private String mountPoint;
    private WipeMethod wipeMethod;
    private String wipeMethodName;
    private WipeStatus status;
    private Integer progressPercent;
    private Long bytesWiped;
    private Double wipeSpeedMbps;
    private Long estimatedTimeRemainingSeconds;
    private Integer currentPass;
    private Integer totalPasses;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private String certificateId;
    private Boolean verificationPassed;
    private Integer verificationSampleCount;
    private Integer verificationMismatchCount;
    private String verificationMismatchLocations;
    private String verificationPattern;
    private LocalDateTime createdAt;
    private Long durationSeconds;
}
