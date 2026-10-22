package com.securewipe.backend.service;

import com.securewipe.backend.entity.WipeJob;
import com.securewipe.backend.enums.WipeStatus;
import com.securewipe.backend.repository.WipeJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs at application startup (before dashboard queries execute) and recovers
 * any wipe jobs that were left in a non-terminal state due to a previous
 * backend crash or manual stop.
 *
 * SAFETY CONTRACT:
 * - Does NOT resume any destructive disk operation.
 * - Does NOT write to any physical drive.
 * - Only updates the STATUS column in the database from PENDING/IN_PROGRESS → FAILED.
 * - Runs with @Order(1) so it completes before any other ApplicationRunner
 *   (e.g. AdminBootstrapService which is not ordered and defaults to lowest priority).
 * - The reason is preserved in error_message so history shows what happened.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class WipeJobRecoveryService implements ApplicationRunner {

    private static final String RECOVERY_REASON =
            "Wipe interrupted: the backend stopped before this operation could complete. " +
            "No data was wiped. This record is preserved for audit purposes.";

    private final WipeJobRepository wipeJobRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        recoverOrphanedJobs();
    }

    @Transactional
    public void recoverOrphanedJobs() {
        List<WipeJob> pending = wipeJobRepository.findByStatus(WipeStatus.PENDING);
        List<WipeJob> inProgress = wipeJobRepository.findByStatus(WipeStatus.IN_PROGRESS);

        int total = pending.size() + inProgress.size();
        if (total == 0) {
            log.info("[RECOVERY] No orphaned wipe jobs found. Dashboard counts are clean.");
            return;
        }

        log.warn("[RECOVERY] Found {} orphaned wipe job(s) (PENDING={}, IN_PROGRESS={}) " +
                 "— marking as FAILED. No disk I/O will be performed.",
                total, pending.size(), inProgress.size());

        LocalDateTime now = LocalDateTime.now();

        for (WipeJob job : pending) {
            log.warn("[RECOVERY] Marking PENDING job {} ({}) as FAILED.",
                    job.getJobId(), job.getDeviceName());
            job.setStatus(WipeStatus.FAILED);
            job.setErrorMessage(RECOVERY_REASON);
            job.setCompletedAt(now);
            // bytesWiped stays 0 — nothing was written for a PENDING job
            wipeJobRepository.save(job);
        }

        for (WipeJob job : inProgress) {
            log.warn("[RECOVERY] Marking IN_PROGRESS job {} ({}) as FAILED. " +
                     "bytesWiped at time of crash was: {} bytes.",
                    job.getJobId(), job.getDeviceName(), job.getBytesWiped());
            job.setStatus(WipeStatus.FAILED);
            job.setErrorMessage(RECOVERY_REASON);
            if (job.getCompletedAt() == null) {
                job.setCompletedAt(now);
            }
            // bytesWiped is preserved — it reflects what was actually written before crash
            wipeJobRepository.save(job);
        }

        log.info("[RECOVERY] Orphan recovery complete. {} job(s) transitioned to FAILED.", total);
    }
}
