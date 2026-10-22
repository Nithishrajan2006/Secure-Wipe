package com.securewipe.backend.service;

import com.securewipe.backend.dto.response.WipeProgressDto;
import com.securewipe.backend.entity.User;
import com.securewipe.backend.entity.WipeJob;
import com.securewipe.backend.enums.WipeStatus;
import com.securewipe.backend.repository.WipeJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Separate bean for async wipe execution.
 *
 * FIX: accepts jobId (String) instead of the WipeJob entity.
 *
 * ROOT CAUSE OF PREVIOUS BUG:
 *   WipeService.startWipe() runs in @Transactional. It saves the WipeJob (INSERT),
 *   then passes the managed entity directly to executeAsync(). When startWipe()'s
 *   transaction commits, that entity becomes DETACHED. Inside executeAsync(), which
 *   runs in a brand-new transaction (@Propagation.REQUIRES_NEW), calling
 *   wipeJobRepository.save(detachedEntity) triggers entityManager.merge().
 *   If the previous transaction has not yet been fully visible to the new transaction,
 *   merge may issue a second INSERT instead of an UPDATE, causing:
 *     Duplicate entry '<job_id>' for key 'wipe_jobs.job_id'
 *
 * FIX: Pass only the jobId string across the transaction boundary.
 *   Inside executeAsync(), load a fresh managed entity using findByJobId().
 *   All subsequent save() calls operate on a properly managed entity
 *   in the correct persistence context → always UPDATE, never INSERT.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WipeExecutorService {

    private final WipeJobRepository wipeJobRepository;
    private final WipeEngineService wipeEngineService;
    private final AuditService auditService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * @param jobId the job_id of the already-persisted WipeJob (NOT the entity itself)
     * @param user  the authenticated user who started the wipe
     */
    @Async("wipeExecutor")
    public void executeAsync(String jobId, User user) {

        // Load a fresh managed entity inside this new transaction.
        // This is the only correct way to work with an entity across transaction boundaries.
        WipeJob job = wipeJobRepository.findByJobId(jobId).orElse(null);
        if (job == null) {
            log.error("[WIPE] executeAsync called for jobId={} but no record found in DB", jobId);
            return;
        }

        try {
            // Mark IN_PROGRESS — this is an UPDATE on a managed entity, never INSERT
            job.setStatus(WipeStatus.IN_PROGRESS);
            job.setStartedAt(LocalDateTime.now());
            wipeJobRepository.save(job);
            log.info("[WIPE] Job {} marked IN_PROGRESS", jobId);

            // Run the real wipe with live progress callbacks
            wipeEngineService.executeWipe(job, progressDto -> {
                updateJobProgress(jobId, progressDto);
                broadcastProgress(jobId, progressDto);
            });

            // Completed — use the in-memory job object that the engine already mutated
            // with verification results via persistVerificationResult().
            //
            // CRITICAL: do NOT reload from DB here for the verification check.
            // persistVerificationResult() sets verificationPassed directly on the
            // job entity that the engine received. If we call findByJobId() again,
            // we get a fresh DB snapshot where verificationPassed is still the
            // default value (false) — because the engine never called
            // wipeJobRepository.save() for the verification fields.
            // Reading verificationPassed from the freshly-fetched entity would
            // therefore always see false and mark the job FAILED regardless of
            // the actual verification outcome.
            boolean verifyOk = Boolean.TRUE.equals(job.getVerificationPassed())
                    || isVerificationSkipped(job);

            if (verifyOk) {
                job.setStatus(WipeStatus.COMPLETED);
                job.setCompletedAt(LocalDateTime.now());
                job.setProgressPercent(100);
                // Use bytesWiped as already persisted by progress callbacks.
                // Fall back to deviceSize only if the engine never emitted a progress update.
                if (job.getBytesWiped() == null || job.getBytesWiped() <= 0) {
                    if (job.getDeviceSize() != null && job.getDeviceSize() > 0) {
                        job.setBytesWiped(job.getDeviceSize());
                    }
                }
                wipeJobRepository.save(job);
                log.info("[WIPE] Job {} marked COMPLETED (verificationPassed={})",
                        jobId, job.getVerificationPassed());
            } else {
                job.setStatus(WipeStatus.FAILED);
                job.setCompletedAt(LocalDateTime.now());
                job.setErrorMessage("Post-wipe verification failed: " +
                        job.getVerificationMismatchLocations());
                wipeJobRepository.save(job);
                log.error("[WIPE] Job {} marked FAILED — verification did not pass. Mismatches: {}",
                        jobId, job.getVerificationMismatchLocations());
            }

            // Broadcast the correct terminal status
            WipeStatus terminalStatus = job.getStatus();
            String terminalMsg = terminalStatus == WipeStatus.COMPLETED
                    ? "✅ Wipe and verification completed successfully!"
                    : "❌ Wipe failed: verification did not pass. See job details.";

            broadcastProgress(jobId, WipeProgressDto.builder()
                    .jobId(jobId)
                    .status(terminalStatus)
                    .progressPercent(terminalStatus == WipeStatus.COMPLETED ? 100 : 0)
                    .statusMessage(terminalMsg)
                    .errorMessage(job.getErrorMessage())
                    .build());

            auditService.log(user, terminalStatus == WipeStatus.COMPLETED ? "WIPE_COMPLETED" : "WIPE_FAILED",
                    "WIPE_JOB", jobId,
                    "Wipe " + terminalStatus.name().toLowerCase() + " on: " + job.getDeviceName(), null);

            log.info("[WIPE] Job {} finished (status={})", jobId, terminalStatus);

        } catch (Exception e) {
            log.error("[WIPE] Job {} failed: {}", jobId, e.getMessage());

            boolean isCancelled = e.getMessage() != null &&
                    (e.getMessage().contains("cancelled") || e instanceof InterruptedException);

            WipeStatus finalStatus = isCancelled ? WipeStatus.CANCELLED : WipeStatus.FAILED;

            wipeJobRepository.findByJobId(jobId).ifPresent(j -> {
                j.setStatus(finalStatus);
                j.setCompletedAt(LocalDateTime.now());
                j.setErrorMessage(e.getMessage());
                wipeJobRepository.save(j);
            });

            broadcastProgress(jobId, WipeProgressDto.builder()
                    .jobId(jobId)
                    .status(finalStatus)
                    .progressPercent(0)
                    .errorMessage(e.getMessage())
                    .statusMessage(isCancelled ? "⚠️ Wipe cancelled" : "❌ Wipe failed: " + e.getMessage())
                    .build());

            auditService.log(user, isCancelled ? "WIPE_CANCELLED" : "WIPE_FAILED",
                    "WIPE_JOB", jobId,
                    "Wipe " + (isCancelled ? "cancelled" : "failed") + " on: " + job.getDeviceName(), null);
        }
    }

    private void updateJobProgress(String jobId, WipeProgressDto progress) {
        try {
            wipeJobRepository.findByJobId(jobId).ifPresent(job -> {
                if (progress.getProgressPercent() != null)
                    job.setProgressPercent(progress.getProgressPercent());
                if (progress.getBytesWiped() != null)
                    job.setBytesWiped(progress.getBytesWiped());
                if (progress.getWipeSpeedMbps() != null)
                    job.setWipeSpeedMbps(progress.getWipeSpeedMbps());
                if (progress.getEstimatedTimeRemainingSeconds() != null)
                    job.setEstimatedTimeRemainingSeconds(progress.getEstimatedTimeRemainingSeconds());
                if (progress.getCurrentPass() != null)
                    job.setCurrentPass(progress.getCurrentPass());
                wipeJobRepository.save(job);
            });
        } catch (Exception e) {
            log.debug("[WIPE] Progress persist failed for {}: {}", jobId, e.getMessage());
        }
    }

    private void broadcastProgress(String jobId, WipeProgressDto progress) {
        try {
            messagingTemplate.convertAndSend("/topic/wipe-progress/" + jobId, progress);
        } catch (Exception e) {
            log.debug("[WIPE] WebSocket broadcast failed for {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Returns true if the verification result stored on the job represents an
     * intentionally skipped verification (e.g. random-fill passes that cannot
     * be re-verified) rather than a genuine failure.
     *
     * A skipped verification is identified by:
     *   - verificationPassed == true  (the engine sets passed=true for skipped)
     *   - verificationMismatchLocations starts with "SKIPPED:"
     *
     * This allows the executor to distinguish:
     *   verificationPassed=true  + no mismatch message → genuinely verified
     *   verificationPassed=true  + "SKIPPED:..." message → intentionally not verified
     *   verificationPassed=false + mismatch message → verification failure → FAILED
     */
    private boolean isVerificationSkipped(WipeJob job) {
        String loc = job.getVerificationMismatchLocations();
        return Boolean.TRUE.equals(job.getVerificationPassed())
                && loc != null && loc.startsWith("SKIPPED:");
    }
}
