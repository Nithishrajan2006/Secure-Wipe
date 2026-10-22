package com.securewipe.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    private long totalWipes;
    private long totalDataWipedBytes;
    private String totalDataWipedFormatted;
    private long activeOperations;
    private double successRate;
    private long completedWipes;
    private long failedWipes;
    private long cancelledWipes;
    private List<WipeJobDto> recentWipes;
    private Map<String, Long> wipesByMethod;
    private Map<String, Long> wipesByStatus;
    private List<DailyWipeStats> dailyStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyWipeStats {
        private String date;
        private long count;
        private long dataWiped;
    }
}
