package com.securewipe.backend.dto.response;

import com.securewipe.backend.enums.WipeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WipeProgressDto {
    private String jobId;
    private WipeStatus status;
    private Integer progressPercent;
    private Long bytesWiped;
    private Long totalBytes;
    private Double wipeSpeedMbps;
    private Long estimatedTimeRemainingSeconds;
    private Integer currentPass;
    private Integer totalPasses;
    private String statusMessage;
    private String errorMessage;
}
