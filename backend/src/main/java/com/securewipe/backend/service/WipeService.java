package com.securewipe.backend.service;

import com.securewipe.backend.dto.request.StartWipeRequest;
import com.securewipe.backend.dto.response.WipeJobDto;
import com.securewipe.backend.entity.User;
import com.securewipe.backend.entity.WipeJob;
import com.securewipe.backend.enums.DeviceType;
import com.securewipe.backend.enums.UserRole;
import com.securewipe.backend.enums.WipeMethod;
import com.securewipe.backend.enums.WipeStatus;
import com.securewipe.backend.exception.BadRequestException;
import com.securewipe.backend.exception.ResourceNotFoundException;
import com.securewipe.backend.repository.WipeJobRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WipeService {

    private final WipeJobRepository wipeJobRepository;
    private final WipeExecutorService wipeExecutorService;
    private final WipeEngineService wipeEngineService;
    private final AuditService auditService;

    @Transactional
    public WipeJobDto startWipe(StartWipeRequest request, User currentUser, HttpServletRequest httpRequest) {
        // Safety gate 1: confirmation text must match device name exactly
        if (!request.getDeviceName().equals(request.getConfirmationText())) {
            throw new BadRequestException(
                "Confirmation text does not match device name. " +
                "Please type the exact device name to proceed.");
        }

        // Safety gate 2: on Windows, only allow removable devices (USB / External HDD)
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        if (isWindows) {
            DeviceType type = request.getDeviceType();
            if (type != DeviceType.USB && type != DeviceType.EXTERNAL_HDD) {
                throw new BadRequestException(
                    "Only removable USB drives can be wiped. " +
                    "Internal drives (HDD/SSD/NVMe) are protected. " +
                    "Selected device type: " + type.getDisplayName());
            }
            // Safety gate 3: block PHYSICALDRIVE0 (system drive)
            String path = request.getDevicePath();
            if (path != null && path.toUpperCase().endsWith("PHYSICALDRIVE0")) {
                throw new BadRequestException(
                    "Cannot wipe PHYSICALDRIVE0 — this is the Windows system drive. " +
                    "Only non-system USB drives may be wiped.");
            }
        }

        String jobId = UUID.randomUUID().toString();
        String certificateId = UUID.randomUUID().toString();

        WipeJob job = WipeJob.builder()
                .jobId(jobId)
                .user(currentUser)
                .deviceName(request.getDeviceName())
                .devicePath(request.getDevicePath())
                .deviceType(request.getDeviceType())
                .deviceSize(request.getDeviceSize() != null ? request.getDeviceSize() : 0L)
                .fileSystem(request.getFileSystem())
                .mountPoint(request.getMountPoint())
                .wipeMethod(request.getWipeMethod())
                .status(WipeStatus.PENDING)
                .totalPasses(request.getWipeMethod().getPasses())
                .certificateId(certificateId)
                .build();

        job = wipeJobRepository.save(job);

        auditService.log(currentUser, "WIPE_STARTED", "WIPE_JOB", jobId,
                "Wipe started — Device: " + request.getDeviceName() +
                " | Method: " + request.getWipeMethod().getDisplayName(), httpRequest);

        // ── Dispatch AFTER transaction commit ─────────────────────────────────────
        // Problem: executeAsync() runs in a new thread. If dispatched directly here,
        // the thread pool may call findByJobId() BEFORE this @Transactional method
        // has committed its INSERT to MySQL — resulting in "no record found".
        //
        // Fix: Register an afterCommit() synchronization callback.
        // Spring only calls afterCommit() after the current transaction has been
        // fully flushed and committed to the database. This guarantees MySQL has
        // the WipeJob row before the async thread tries to read it.
        final String committedJobId = job.getJobId();
        final User committedUser    = currentUser;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("[WIPE] Transaction committed — dispatching async wipe for job {}", committedJobId);
                wipeExecutorService.executeAsync(committedJobId, committedUser);
            }
        });

        return toDto(job);
    }

    @Transactional
    public WipeJobDto cancelWipe(String jobId, User currentUser, HttpServletRequest httpRequest) {
        WipeJob job = wipeJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("WipeJob", "jobId", jobId));

        if (!job.getUser().getId().equals(currentUser.getId()) &&
                currentUser.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("You are not authorised to cancel this job");
        }

        if (job.getStatus() != WipeStatus.IN_PROGRESS && job.getStatus() != WipeStatus.PENDING) {
            throw new BadRequestException("Cannot cancel a job with status: " + job.getStatus());
        }

        wipeEngineService.cancelWipe(jobId);

        auditService.log(currentUser, "WIPE_CANCEL_REQUESTED", "WIPE_JOB", jobId,
                "Cancel requested for: " + job.getDeviceName(), httpRequest);

        return toDto(job);
    }

    @Transactional(readOnly = true)
    public WipeJobDto getWipeProgress(String jobId) {
        WipeJob job = wipeJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("WipeJob", "jobId", jobId));
        return toDto(job);
    }

    @Transactional(readOnly = true)
    public Page<WipeJobDto> getWipeHistory(User currentUser, WipeStatus status,
                                            String method, LocalDateTime from,
                                            LocalDateTime to, Pageable pageable) {
        // Parse the method enum — null means "all methods"
        WipeMethod wipeMethod = null;
        if (method != null && !method.isBlank()) {
            try {
                wipeMethod = WipeMethod.valueOf(method.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[HISTORY] Unknown wipe method filter value '{}' — ignoring", method);
            }
        }

        if (currentUser.getRole() == UserRole.ADMIN) {
            return wipeJobRepository.findAllByFilters(status, wipeMethod, from, to, pageable)
                    .map(this::toDto);
        }
        return wipeJobRepository.findByFilters(currentUser, status, wipeMethod, from, to, pageable)
                .map(this::toDto);
    }

    // ── DTO Mapping ─────────────────────────────────────────────────────────────

    WipeJobDto toDto(WipeJob job) {
        long durationSeconds = 0;
        if (job.getStartedAt() != null) {
            LocalDateTime end = job.getCompletedAt() != null ? job.getCompletedAt() : LocalDateTime.now();
            durationSeconds = ChronoUnit.SECONDS.between(job.getStartedAt(), end);
        }

        return WipeJobDto.builder()
                .id(job.getId())
                .jobId(job.getJobId())
                .userId(job.getUser().getId())
                .username(job.getUser().getUsername())
                .deviceName(job.getDeviceName())
                .devicePath(job.getDevicePath())
                .deviceType(job.getDeviceType())
                .deviceSize(job.getDeviceSize())
                .deviceSizeFormatted(formatSize(job.getDeviceSize()))
                .fileSystem(job.getFileSystem())
                .mountPoint(job.getMountPoint())
                .wipeMethod(job.getWipeMethod())
                .wipeMethodName(job.getWipeMethod().getDisplayName())
                .status(job.getStatus())
                .progressPercent(job.getProgressPercent())
                .bytesWiped(job.getBytesWiped())
                .wipeSpeedMbps(job.getWipeSpeedMbps())
                .estimatedTimeRemainingSeconds(job.getEstimatedTimeRemainingSeconds())
                .currentPass(job.getCurrentPass())
                .totalPasses(job.getTotalPasses())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .certificateId(job.getCertificateId())
                .verificationPassed(job.getVerificationPassed())
                .verificationSampleCount(job.getVerificationSampleCount())
                .verificationMismatchCount(job.getVerificationMismatchCount())
                .verificationMismatchLocations(job.getVerificationMismatchLocations())
                .verificationPattern(job.getVerificationPattern())
                .createdAt(job.getCreatedAt())
                .durationSeconds(durationSeconds)
                .build();
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = (int) (Math.log10(bytes + 1) / Math.log10(1024));
        idx = Math.min(idx, units.length - 1);
        return String.format("%.1f %s", bytes / Math.pow(1024, idx), units[idx]);
    }
}
