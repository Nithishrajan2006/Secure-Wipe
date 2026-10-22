package com.securewipe.backend.service;

import com.securewipe.backend.dto.response.DashboardStatsDto;
import com.securewipe.backend.dto.response.WipeJobDto;
import com.securewipe.backend.entity.User;
import com.securewipe.backend.entity.WipeJob;
import com.securewipe.backend.enums.UserRole;
import com.securewipe.backend.enums.WipeStatus;
import com.securewipe.backend.repository.WipeJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final WipeJobRepository wipeJobRepository;

    private static final List<WipeStatus> ACTIVE_STATUSES =
            List.of(WipeStatus.IN_PROGRESS);   // PENDING is NOT active — it means queued but not started

    @Transactional(readOnly = true)
    public DashboardStatsDto getStats(User currentUser) {
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;

        long totalWipes = isAdmin
                ? wipeJobRepository.count()
                : wipeJobRepository.countByUser(currentUser);

        long completedWipes = isAdmin
                ? wipeJobRepository.countByStatus(WipeStatus.COMPLETED)
                : wipeJobRepository.countByUserAndStatus(currentUser, WipeStatus.COMPLETED);

        long failedWipes = isAdmin
                ? wipeJobRepository.countByStatus(WipeStatus.FAILED)
                : wipeJobRepository.countByUserAndStatus(currentUser, WipeStatus.FAILED);

        long cancelledWipes = isAdmin
                ? wipeJobRepository.countByStatus(WipeStatus.CANCELLED)
                : wipeJobRepository.countByUserAndStatus(currentUser, WipeStatus.CANCELLED);

        // "Total Data Wiped" = SUM of bytes_wiped from COMPLETED jobs only.
        // bytes_wiped tracks actual bytes written by the wipe engine — not device_size.
        // FAILED/CANCELLED jobs are excluded entirely: they did not complete the wipe.
        Long rawDataWiped = isAdmin
                ? wipeJobRepository.sumBytesWipedByStatus(WipeStatus.COMPLETED)
                : wipeJobRepository.sumBytesWipedByUserAndStatus(currentUser, WipeStatus.COMPLETED);
        long totalDataWiped = rawDataWiped != null ? rawDataWiped : 0L;

        // Active = only genuinely executing jobs (IN_PROGRESS).
        // PENDING jobs are not yet started — the recovery service marks them FAILED on restart,
        // so in normal operation there should be 0 PENDING jobs unless one was just submitted.
        long activeOps = isAdmin
                ? wipeJobRepository.countByStatusIn(ACTIVE_STATUSES)
                : wipeJobRepository.countByUserAndStatusIn(currentUser, ACTIVE_STATUSES);

        // Success rate = completed / (completed + failed + cancelled) * 100
        // Active/pending jobs are excluded from the denominator — they are not yet resolved.
        long terminalJobs = completedWipes + failedWipes + cancelledWipes;
        double successRate = terminalJobs > 0
                ? (double) completedWipes / terminalJobs * 100.0
                : 0.0;

        List<WipeJob> recentJobs = isAdmin
                ? wipeJobRepository.findAllRecent(PageRequest.of(0, 10))
                : wipeJobRepository.findRecentByUser(currentUser, PageRequest.of(0, 10));

        List<WipeJobDto> recentWipes = recentJobs.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        List<WipeJob> allRecent = isAdmin
                ? wipeJobRepository.findAllRecent(PageRequest.of(0, 200))
                : wipeJobRepository.findRecentByUser(currentUser, PageRequest.of(0, 200));

        Map<String, Long> wipesByMethod = allRecent.stream()
                .collect(Collectors.groupingBy(
                        j -> j.getWipeMethod().getDisplayName(),
                        Collectors.counting()));

        Map<String, Long> wipesByStatus = allRecent.stream()
                .collect(Collectors.groupingBy(
                        j -> j.getStatus().name(),
                        Collectors.counting()));

        List<DashboardStatsDto.DailyWipeStats> dailyStats = buildDailyStats(allRecent);

        return DashboardStatsDto.builder()
                .totalWipes(totalWipes)
                .totalDataWipedBytes(totalDataWiped)
                .totalDataWipedFormatted(formatSize(totalDataWiped))
                .activeOperations(activeOps)
                .successRate(Math.round(successRate * 10.0) / 10.0)
                .completedWipes(completedWipes)
                .failedWipes(failedWipes)
                .cancelledWipes(cancelledWipes)
                .recentWipes(recentWipes)
                .wipesByMethod(wipesByMethod)
                .wipesByStatus(wipesByStatus)
                .dailyStats(dailyStats)
                .build();
    }

    private List<DashboardStatsDto.DailyWipeStats> buildDailyStats(List<WipeJob> jobs) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, List<WipeJob>> byDate = new LinkedHashMap<>();

        for (int i = 6; i >= 0; i--) {
            byDate.put(LocalDateTime.now().minusDays(i).format(formatter), new ArrayList<>());
        }

        for (WipeJob job : jobs) {
            String date = job.getCreatedAt().format(formatter);
            if (byDate.containsKey(date)) {
                byDate.get(date).add(job);
            }
        }

        return byDate.entrySet().stream()
                .map(entry -> DashboardStatsDto.DailyWipeStats.builder()
                        .date(entry.getKey())
                        .count((long) entry.getValue().size())
                        .dataWiped(entry.getValue().stream()
                                .filter(j -> j.getStatus() == WipeStatus.COMPLETED)
                                .mapToLong(j -> j.getDeviceSize() != null ? j.getDeviceSize() : 0L)
                                .sum())
                        .build())
                .collect(Collectors.toList());
    }

    private WipeJobDto toDto(WipeJob job) {
        return WipeJobDto.builder()
                .id(job.getId())
                .jobId(job.getJobId())
                .userId(job.getUser().getId())
                .username(job.getUser().getUsername())
                .deviceName(job.getDeviceName())
                .devicePath(job.getDevicePath())
                .deviceType(job.getDeviceType())
                .deviceSize(job.getDeviceSize())
                .deviceSizeFormatted(formatSize(job.getDeviceSize() != null ? job.getDeviceSize() : 0L))
                .wipeMethod(job.getWipeMethod())
                .wipeMethodName(job.getWipeMethod().getDisplayName())
                .status(job.getStatus())
                .progressPercent(job.getProgressPercent())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .certificateId(job.getCertificateId())
                .createdAt(job.getCreatedAt())
                .build();
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = (int) (Math.log10(bytes + 1) / Math.log10(1024));
        idx = Math.min(idx, units.length - 1);
        return String.format("%.1f %s", bytes / Math.pow(1024, idx), units[idx]);
    }
}
