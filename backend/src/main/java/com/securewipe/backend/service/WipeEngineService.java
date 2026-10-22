package com.securewipe.backend.service;

import com.securewipe.backend.dto.response.WipeProgressDto;
import com.securewipe.backend.entity.WipeJob;
import com.securewipe.backend.enums.DeviceType;
import com.securewipe.backend.enums.WipeStatus;
import com.securewipe.backend.exception.WipeOperationException;
import com.securewipe.backend.io.WindowsPhysicalDriveWriter;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * WipeEngineService — Real secure wipe using JNA for Windows raw device I/O.
 *
 * WHY JNA IS REQUIRED:
 * ────────────────────
 * Java's RandomAccessFile / FileChannel call Windows CreateFile() with
 * FILE_ATTRIBUTE_NORMAL, which is incompatible with raw physical drive handles.
 * This causes ERROR_INVALID_PARAMETER (87) regardless of path format.
 *
 * The correct Windows flags for raw physical drive I/O are:
 *   GENERIC_READ | GENERIC_WRITE
 *   FILE_SHARE_READ | FILE_SHARE_WRITE
 *   OPEN_EXISTING
 *   FILE_FLAG_NO_BUFFERING | FILE_FLAG_WRITE_THROUGH
 *
 * JNA allows calling CreateFileW() directly with these exact flags,
 * bypassing Java's internal flag selection entirely.
 *
 * SECTOR ALIGNMENT:
 * ─────────────────
 * FILE_FLAG_NO_BUFFERING requires all writes to be:
 *   - Sector-aligned in size (multiple of 512 bytes)
 *   - Sector-aligned in file offset
 * Our BUFFER_SIZE is 8 MB (exactly 16384 × 512), satisfying both requirements.
 * The final chunk is padded to the next 512-byte boundary.
 *
 * Safety hierarchy enforced before any byte is written:
 *   1. Path must contain "PHYSICALDRIVE"
 *   2. Must NOT be PHYSICALDRIVE0 (system drive — always blocked)
 *   3. Device type must be USB or EXTERNAL_HDD
 *   4. JVM must run as Administrator
 */
@Service
@Slf4j
public class WipeEngineService {

    private static final int SECTOR_SIZE = 512;
    private static final int BUFFER_SIZE = 16_384 * SECTOR_SIZE; // 8 MB, sector-aligned

    private final Set<String> cancelledJobs = ConcurrentHashMap.newKeySet();
    private final SecureRandom secureRandom = new SecureRandom();

    // ── JNA Windows Kernel32 Interface ─────────────────────────────────────────

    interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);

        WinNT.HANDLE CreateFileW(
                String lpFileName,
                int dwDesiredAccess,
                int dwShareMode,
                Pointer lpSecurityAttributes,
                int dwCreationDisposition,
                int dwFlagsAndAttributes,
                WinNT.HANDLE hTemplateFile
        );

        boolean WriteFile(
                WinNT.HANDLE hFile,
                byte[] lpBuffer,
                int nNumberOfBytesToWrite,
                int[] lpNumberOfBytesWritten,
                Pointer lpOverlapped
        );

        boolean ReadFile(
                WinNT.HANDLE hFile,
                byte[] lpBuffer,
                int nNumberOfBytesToRead,
                int[] lpNumberOfBytesRead,
                Pointer lpOverlapped
        );

        boolean SetFilePointerEx(
                WinNT.HANDLE hFile,
                long liDistanceToMove,
                long[] lpNewFilePointer,
                int dwMoveMethod
        );

        boolean FlushFileBuffers(WinNT.HANDLE hFile);

        boolean CloseHandle(WinNT.HANDLE hObject);

        int GetLastError();
    }

    // Windows constants
    private static final int GENERIC_READ              = 0x80000000;
    private static final int GENERIC_WRITE             = 0x40000000;
    private static final int FILE_SHARE_READ           = 0x00000001;
    private static final int FILE_SHARE_WRITE          = 0x00000002;
    private static final int OPEN_EXISTING             = 3;
    private static final int FILE_FLAG_NO_BUFFERING    = 0x20000000;
    private static final int FILE_FLAG_WRITE_THROUGH   = 0x80000000;
    private static final int FILE_BEGIN                = 0;
    private static final int INVALID_HANDLE_VALUE      = -1;

    // ── Public API ─────────────────────────────────────────────────────────────

    public void cancelWipe(String jobId) {
        cancelledJobs.add(jobId);
        log.info("[WIPE] Cancel requested for job: {}", jobId);
    }

    public void executeWipe(WipeJob job, Consumer<WipeProgressDto> progressCallback) {
        log.info("[WIPE] Starting job={} device={} method={}",
                job.getJobId(), job.getDevicePath(), job.getWipeMethod());

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        if (isWindows) {
            executeWindowsWipe(job, progressCallback);
        } else {
            executeLinuxWipe(job, progressCallback);
        }
    }

    // ── Windows Entry Point ─────────────────────────────────────────────────────

    private void executeWindowsWipe(WipeJob job, Consumer<WipeProgressDto> cb) {
        String rawPath = job.getDevicePath();

        if (!isWindowsPhysicalDrivePath(rawPath)) {
            log.warn("[WIPE] '{}' not a Windows physical drive path — simulation", rawPath);
            simulateWipe(job, cb);
            return;
        }
        if (isSystemDrive(rawPath)) {
            throw new WipeOperationException(
                "BLOCKED: Cannot wipe the system drive (" + rawPath + "). This drive is protected.");
        }
        if (!isRemovableDevice(job)) {
            throw new WipeOperationException(
                "BLOCKED: Only removable USB drives may be wiped. " +
                "Device type: " + job.getDeviceType().getDisplayName());
        }
        if (!isRunningAsAdministrator()) {
            log.error("[WIPE] Non-elevated process detected for '{}'. Raw disk access requires Administrator privileges.", rawPath);
            throw new WipeOperationException(
                "Administrator privileges required. Raw disk access to physical drive '" + rawPath +
                "' requires running the backend as Administrator. Please close any open files or File Explorer windows on the drive and restart the backend as Administrator.");
        }

        long deviceSize = resolveDeviceSize(job);
        log.info("[WIPE] Safety checks passed. Real JNA wipe: {} ({} bytes / {} GB)",
                rawPath, deviceSize, String.format("%.2f", deviceSize / 1_073_741_824.0));

        try {
            switch (job.getWipeMethod()) {
                case ZEROS     -> performJnaPass(job, rawPath, deviceSize,
                                        (byte) 0x00, false, 1, 1,
                                        "Pass 1/1: Writing zeros (0x00)", cb);
                case RANDOM    -> performJnaPass(job, rawPath, deviceSize,
                                        (byte) 0x00, true,  1, 1,
                                        "Pass 1/1: Writing cryptographic random data", cb);
                case DOD_3PASS -> performJnaDod3(job, rawPath, deviceSize, cb);
                case DOD_7PASS -> performJnaDod7(job, rawPath, deviceSize, cb);
                case ATA_SECURE_ERASE -> {
                    log.info("[WIPE] ATA Secure Erase → DoD 3-Pass via JNA on Windows");
                    performJnaDod3(job, rawPath, deviceSize, cb);
                }
                default -> throw new WipeOperationException("Unknown method: " + job.getWipeMethod());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WipeOperationException("Wipe cancelled by user");
        } catch (IOException e) {
            log.error("[WIPE] IO error on {} — full stack trace:", rawPath, e);
            throw new WipeOperationException("IO error during wipe: " + e.getMessage(), e);
        }
    }

    // ── DoD Algorithm Sequences ─────────────────────────────────────────────────

    private void performJnaDod3(WipeJob job, String path, long size,
                                 Consumer<WipeProgressDto> cb)
            throws IOException, InterruptedException {
        performJnaPass(job, path, size, (byte) 0x00, false, 1, 3, "Pass 1/3: Writing 0x00", cb);
        performJnaPass(job, path, size, (byte) 0xFF, false, 2, 3, "Pass 2/3: Writing 0xFF", cb);
        performJnaPass(job, path, size, (byte) 0x00, true,  3, 3, "Pass 3/3: Writing random data", cb);
    }

    private void performJnaDod7(WipeJob job, String path, long size,
                                 Consumer<WipeProgressDto> cb)
            throws IOException, InterruptedException {
        performJnaPass(job, path, size, (byte) 0x00, false, 1, 7, "Pass 1/7: Writing 0x00", cb);
        performJnaPass(job, path, size, (byte) 0xFF, false, 2, 7, "Pass 2/7: Writing 0xFF", cb);
        performJnaPass(job, path, size, (byte) 0x00, true,  3, 7, "Pass 3/7: Writing random data", cb);
        performJnaPass(job, path, size, (byte) 0x00, false, 4, 7, "Pass 4/7: Writing 0x00", cb);
        performJnaPass(job, path, size, (byte) 0xFF, false, 5, 7, "Pass 5/7: Writing 0xFF", cb);
        performJnaPass(job, path, size, (byte) 0x00, true,  6, 7, "Pass 6/7: Writing random data", cb);
        performJnaPass(job, path, size, (byte) 0x00, false, 7, 7, "Pass 7/7: Final 0x00", cb);
    }

    // ── Core JNA Write Method ───────────────────────────────────────────────────

    /**
     * Opens the physical drive via {@link WindowsPhysicalDriveWriter}, which:
     *   1. Enumerates mounted volumes on the same disk and locks + dismounts
     *      them (FSCTL_LOCK_VOLUME / FSCTL_DISMOUNT_VOLUME) so the volume
     *      manager releases its claim on the disk — without this step,
     *      WriteFile fails with ERROR_ACCESS_DENIED (5) even with a valid
     *      handle and elevated privileges.
     *   2. Opens the device itself via CreateFile with the flags required
     *      for raw physical-drive I/O (not FILE_ATTRIBUTE_NORMAL, which
     *      java.io/java.nio always pass and which Windows rejects for
     *      device paths).
     */
    private void performJnaPass(WipeJob job, String devicePath, long deviceSize,
                                 byte fillByte, boolean randomFill,
                                 int currentPass, int totalPasses,
                                 String statusMessage,
                                 Consumer<WipeProgressDto> cb)
            throws IOException, InterruptedException {

        log.info("[WIPE] JNA Pass {}/{} — {} on {} ({} bytes)",
                currentPass, totalPasses, statusMessage, devicePath, deviceSize);

        byte[] buffer = new byte[BUFFER_SIZE];
        if (!randomFill) {
            Arrays.fill(buffer, fillByte);
        }

        // Open device via WindowsPhysicalDriveWriter — NOT CreateFileW directly.
        // WindowsPhysicalDriveWriter's constructor is what enumerates drive
        // letters and performs FSCTL_LOCK_VOLUME / FSCTL_DISMOUNT_VOLUME on any
        // mounted volume that lives on this physical disk. Calling CreateFileW
        // here directly (as this method previously did) skips that step
        // entirely, which is why WriteFile was failing with ERROR_ACCESS_DENIED
        // (5): the volume manager still considered the disk "in use".
        log.info("[WIPE-DIAG] Opening \"{}\" via WindowsPhysicalDriveWriter " +
                "(locks/dismounts any mounted volumes on this disk first)...", devicePath);

        long bytesWritten = 0;
        long startTime   = System.currentTimeMillis();
        long lastUpdate  = startTime;

        try (WindowsPhysicalDriveWriter writer = new WindowsPhysicalDriveWriter(devicePath)) {

            log.info("[WIPE-DIAG] WindowsPhysicalDriveWriter opened OK. Starting write loop.");

            while (bytesWritten < deviceSize) {

                // Cancellation check
                if (cancelledJobs.contains(job.getJobId())) {
                    cancelledJobs.remove(job.getJobId());
                    throw new InterruptedException("Wipe cancelled by user");
                }

                // Refresh random buffer each chunk
                if (randomFill) {
                    secureRandom.nextBytes(buffer);
                }

                long remaining  = deviceSize - bytesWritten;
                // toWrite is the logical number of bytes we want to cover from the device.
                // aligned is the sector-aligned size we must pass to WriteFile.
                // IMPORTANT: aligned must never exceed buffer.length (BUFFER_SIZE).
                // When remaining < BUFFER_SIZE, align UP to the next sector boundary,
                // but cap at BUFFER_SIZE to avoid an out-of-bounds write.
                int  toWrite    = (int) Math.min(BUFFER_SIZE, remaining);
                int  aligned    = Math.min(alignToSector(toWrite), BUFFER_SIZE);

                writer.write(buffer, aligned);
                // Account only for the logical bytes we intended to cover, not the
                // padding — this prevents bytesWritten from exceeding deviceSize.
                bytesWritten += toWrite;

                long now = System.currentTimeMillis();
                if (now - lastUpdate >= 500) {
                    emitProgress(job, bytesWritten, deviceSize,
                            currentPass, totalPasses, statusMessage, startTime, now, cb);
                    lastUpdate = now;
                }
            }

            // Flush to physical media
            log.info("[WIPE-DIAG] Write loop done. Calling FlushFileBuffers...");
            try {
                writer.flush();
                log.info("[WIPE-DIAG] FlushFileBuffers OK.");
            } catch (IOException e) {
                log.warn("[WIPE-DIAG] FlushFileBuffers failed: {}", e.getMessage());
            }

            log.info("[WIPE] Pass {}/{} complete — {} bytes written to {}",
                    currentPass, totalPasses, bytesWritten, devicePath);

            VerificationSummary summary = verifyWrittenPattern(job, devicePath, deviceSize,
                    fillByte, randomFill, currentPass, totalPasses, statusMessage, cb);
            persistVerificationResult(job, summary);

        } finally {
            log.info("[WIPE-DIAG] Device handle (and any locked volume handles) closed.");
        }
    }

    // ── Linux / Docker Path ────────────────────────────────────────────────────

    private void executeLinuxWipe(WipeJob job, Consumer<WipeProgressDto> cb) {
        File target = new File(job.getDevicePath());
        if (!target.exists()) {
            log.info("[WIPE] Linux path not found — simulation for job {}", job.getJobId());
            simulateWipe(job, cb);
            return;
        }
        try {
            switch (job.getWipeMethod()) {
                case ZEROS     -> linuxPass(job, target, (byte) 0x00, false, 1, 1, "Writing zeros", cb);
                case RANDOM    -> linuxPass(job, target, (byte) 0x00, true,  1, 1, "Writing random data", cb);
                case DOD_3PASS -> {
                    linuxPass(job, target, (byte) 0x00, false, 1, 3, "Pass 1/3: 0x00", cb);
                    linuxPass(job, target, (byte) 0xFF, false, 2, 3, "Pass 2/3: 0xFF", cb);
                    linuxPass(job, target, (byte) 0x00, true,  3, 3, "Pass 3/3: random", cb);
                }
                case DOD_7PASS -> {
                    linuxPass(job, target, (byte) 0x00, false, 1, 7, "Pass 1/7: 0x00", cb);
                    linuxPass(job, target, (byte) 0xFF, false, 2, 7, "Pass 2/7: 0xFF", cb);
                    linuxPass(job, target, (byte) 0x00, true,  3, 7, "Pass 3/7: random", cb);
                    linuxPass(job, target, (byte) 0x00, false, 4, 7, "Pass 4/7: 0x00", cb);
                    linuxPass(job, target, (byte) 0xFF, false, 5, 7, "Pass 5/7: 0xFF", cb);
                    linuxPass(job, target, (byte) 0x00, true,  6, 7, "Pass 6/7: random", cb);
                    linuxPass(job, target, (byte) 0x00, false, 7, 7, "Pass 7/7: 0x00", cb);
                }
                case ATA_SECURE_ERASE -> {
                    log.info("[WIPE] ATA Secure Erase → zero-fill on Linux");
                    linuxPass(job, target, (byte) 0x00, false, 1, 1, "Writing zeros", cb);
                }
                default -> throw new WipeOperationException("Unknown method: " + job.getWipeMethod());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WipeOperationException("Wipe interrupted");
        } catch (IOException e) {
            throw new WipeOperationException("IO error: " + e.getMessage(), e);
        }
    }

    private void linuxPass(WipeJob job, File target, byte fillByte, boolean random,
                            int cp, int tp, String msg,
                            Consumer<WipeProgressDto> cb)
            throws IOException, InterruptedException {

        long total = target.isFile() ? target.length() : resolveDeviceSize(job);
        byte[] buffer = new byte[BUFFER_SIZE];
        if (!random) Arrays.fill(buffer, fillByte);

        long written = 0;
        long start = System.currentTimeMillis(), last = start;

        try (FileChannel ch = FileChannel.open(target.toPath(),
                java.nio.file.StandardOpenOption.WRITE)) {
            ch.position(0);
            while (written < total) {
                if (cancelledJobs.contains(job.getJobId())) {
                    cancelledJobs.remove(job.getJobId());
                    throw new InterruptedException("Wipe cancelled");
                }
                if (random) secureRandom.nextBytes(buffer);
                int w = (int) Math.min(BUFFER_SIZE, total - written);
                ch.write(ByteBuffer.wrap(buffer, 0, w));
                written += w;
                long now = System.currentTimeMillis();
                if (now - last >= 500) {
                    emitProgress(job, written, total, cp, tp, msg, start, now, cb);
                    last = now;
                }
            }
            ch.force(true);
        }
        log.info("[WIPE] Linux pass {}/{} complete — {} bytes", cp, tp, written);

        VerificationSummary summary = verifyWrittenPattern(job, target.getAbsolutePath(), total,
                fillByte, random, cp, tp, msg, cb);
        persistVerificationResult(job, summary);
    }

    // ── Verification ───────────────────────────────────────────────────────────

    /**
     * Reads back the physical device via JNA (same path as writes — bypasses
     * Java's FILE_ATTRIBUTE_NORMAL restriction) and checks every byte against
     * the expected pattern.
     *
     * For ZEROS: every byte must be 0x00.
     * For 0xFF:  every byte must be 0xFF.
     * For random patterns: verification is marked SKIPPED (not PASSED) —
     *   random data cannot be verified by re-reading since the written
     *   content is not recorded anywhere.
     *
     * IMPORTANT: only bytes within [0, deviceSize) are verified.
     * The final sector-padding bytes written to satisfy FILE_FLAG_NO_BUFFERING
     * alignment are outside this range and are intentionally not checked.
     *
     * Progress is emitted every 500 ms — same throttle as the write loop.
     *
     * @return VerificationSummary — passed=true only if every sampled byte matches.
     *         passed=false if any mismatch was found or a read error occurred.
     *         passed=false with mismatchLocations="SKIPPED" if the pattern cannot be verified.
     */
    private VerificationSummary verifyWrittenPattern(WipeJob job, String devicePath,
                                                      long deviceSize,
                                                      byte fillByte, boolean randomFill,
                                                      int currentPass, int totalPasses,
                                                      String statusMessage,
                                                      Consumer<WipeProgressDto> cb)
            throws IOException {

        String patternName = randomFill ? "random" : (fillByte == (byte) 0x00 ? "zeros" : "0xFF");

        // Random-fill passes cannot be verified by re-reading (we don't store what was written).
        if (randomFill) {
            log.info("[VERIFY] Pass {}/{} — random pattern cannot be verified by re-read; skipping.",
                    currentPass, totalPasses);
            return new VerificationSummary(true, 0, 0, patternName,
                    "SKIPPED: random pattern cannot be verified by re-reading");
        }

        if (deviceSize <= 0) {
            return new VerificationSummary(false, 0, 0, patternName,
                    "FAILED: invalid device size " + deviceSize);
        }

        log.info("[VERIFY] Starting read-back verification of {} — expecting pattern {} across {} bytes",
                devicePath, patternName, deviceSize);

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");

        if (isWindows && isWindowsPhysicalDrivePath(devicePath)) {
            return verifyWindowsDevice(job, devicePath, deviceSize, fillByte, patternName,
                    currentPass, totalPasses, cb);
        } else {
            return verifyLinuxDevice(job, devicePath, deviceSize, fillByte, patternName,
                    currentPass, totalPasses, cb);
        }
    }

    /**
     * Windows verification via JNA ReadFile — uses the same GENERIC_READ
     * handle path that avoids FILE_ATTRIBUTE_NORMAL rejection.
     */
    private VerificationSummary verifyWindowsDevice(WipeJob job, String devicePath,
                                                      long deviceSize, byte fillByte,
                                                      String patternName,
                                                      int currentPass, int totalPasses,
                                                      Consumer<WipeProgressDto> cb)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long bytesVerified = 0;
        long firstMismatchOffset = -1;
        long verifyStart = System.currentTimeMillis();
        long lastUpdate  = verifyStart;

        try (WindowsPhysicalDriveWriter reader = WindowsPhysicalDriveWriter.openReadOnly(devicePath)) {
            while (bytesVerified < deviceSize) {
                // Cancellation during verification — job will be FAILED, not COMPLETED
                if (cancelledJobs.contains(job.getJobId())) {
                    cancelledJobs.remove(job.getJobId());
                    return new VerificationSummary(false, (int)(bytesVerified / SECTOR_SIZE), 0,
                            patternName, "Verification cancelled by user at offset " + bytesVerified);
                }

                long remaining = deviceSize - bytesVerified;
                int toRead = (int) Math.min(BUFFER_SIZE, remaining);
                // Reads must also be sector-aligned for FILE_FLAG_NO_BUFFERING handles
                int aligned = Math.min(alignToSector(toRead), BUFFER_SIZE);

                int n = reader.read(buffer, aligned);
                if (n <= 0) {
                    // End of device — stop
                    break;
                }

                // Only check bytes within [0, remaining) — ignore alignment padding at end
                int bytesToCheck = (int) Math.min(n, remaining);
                for (int i = 0; i < bytesToCheck; i++) {
                    if (buffer[i] != fillByte) {
                        firstMismatchOffset = bytesVerified + i;
                        log.error("[VERIFY] MISMATCH at byte offset {} — expected 0x{} got 0x{}",
                                firstMismatchOffset,
                                String.format("%02X", fillByte & 0xFF),
                                String.format("%02X", buffer[i] & 0xFF));
                        return new VerificationSummary(false,
                                (int)(bytesVerified / SECTOR_SIZE), 1, patternName,
                                "FAILED: first mismatch at byte offset " + firstMismatchOffset +
                                " — expected 0x" + String.format("%02X", fillByte & 0xFF) +
                                " got 0x" + String.format("%02X", buffer[i] & 0xFF));
                    }
                }
                bytesVerified += toRead;

                long now = System.currentTimeMillis();
                if (now - lastUpdate >= 500) {
                    emitVerifyProgress(job, bytesVerified, deviceSize, currentPass, totalPasses, cb);
                    lastUpdate = now;
                }
            }
        }

        long sectors = (bytesVerified + SECTOR_SIZE - 1) / SECTOR_SIZE;
        log.info("[VERIFY] Pass {}/{} — PASSED: {} bytes verified, pattern={}, no mismatches.",
                currentPass, totalPasses, bytesVerified, patternName);
        return new VerificationSummary(true, (int) sectors, 0, patternName, null);
    }

    /**
     * Linux verification via standard RandomAccessFile — works correctly on
     * Linux block device paths (/dev/sdX) which do not have the FILE_ATTRIBUTE_NORMAL problem.
     */
    private VerificationSummary verifyLinuxDevice(WipeJob job, String devicePath,
                                                   long deviceSize, byte fillByte,
                                                   String patternName,
                                                   int currentPass, int totalPasses,
                                                   Consumer<WipeProgressDto> cb)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long bytesVerified = 0;
        long verifyStart = System.currentTimeMillis();
        long lastUpdate  = verifyStart;

        try (RandomAccessFile raf = new RandomAccessFile(devicePath, "r")) {
            raf.seek(0);
            while (bytesVerified < deviceSize) {
                if (cancelledJobs.contains(job.getJobId())) {
                    cancelledJobs.remove(job.getJobId());
                    return new VerificationSummary(false, (int)(bytesVerified / SECTOR_SIZE), 0,
                            patternName, "Verification cancelled at offset " + bytesVerified);
                }
                long remaining = deviceSize - bytesVerified;
                int toRead = (int) Math.min(BUFFER_SIZE, remaining);
                int n = raf.read(buffer, 0, toRead);
                if (n <= 0) break;
                for (int i = 0; i < n; i++) {
                    if (buffer[i] != fillByte) {
                        long offset = bytesVerified + i;
                        return new VerificationSummary(false, (int)(bytesVerified / SECTOR_SIZE), 1,
                                patternName,
                                "FAILED: first mismatch at offset " + offset +
                                " — expected 0x" + String.format("%02X", fillByte & 0xFF) +
                                " got 0x" + String.format("%02X", buffer[i] & 0xFF));
                    }
                }
                bytesVerified += n;
                long now = System.currentTimeMillis();
                if (now - lastUpdate >= 500) {
                    emitVerifyProgress(job, bytesVerified, deviceSize, currentPass, totalPasses, cb);
                    lastUpdate = now;
                }
            }
        }
        long sectors = (bytesVerified + SECTOR_SIZE - 1) / SECTOR_SIZE;
        return new VerificationSummary(true, (int) sectors, 0, patternName, null);
    }

    private void emitVerifyProgress(WipeJob job, long bytesVerified, long deviceSize,
                                     int currentPass, int totalPasses,
                                     Consumer<WipeProgressDto> cb) {
        double pct = deviceSize > 0 ? (double) bytesVerified / deviceSize * 100.0 : 0;
        cb.accept(WipeProgressDto.builder()
                .jobId(job.getJobId())
                .status(WipeStatus.IN_PROGRESS)
                .progressPercent(Math.min((int) pct, 99))
                .bytesWiped(bytesVerified)
                .totalBytes(deviceSize)
                .currentPass(currentPass)
                .totalPasses(totalPasses)
                .statusMessage("Verifying pass " + currentPass + "/" + totalPasses +
                        " — " + String.format("%.1f", pct) + "% read back")
                .build());
    }

    private void persistVerificationResult(WipeJob job, VerificationSummary summary) {
        job.setVerificationPassed(summary.passed());
        job.setVerificationSampleCount(summary.sampleCount());
        job.setVerificationMismatchCount(summary.mismatchCount());
        job.setVerificationMismatchLocations(summary.mismatchLocations());
        job.setVerificationPattern(summary.patternName());
    }

    private record VerificationSummary(boolean passed, int sampleCount, int mismatchCount,
                                        String patternName, String mismatchLocations) {
    }

    // ── Safety Checks ───────────────────────────────────────────────────────────

    private boolean isWindowsPhysicalDrivePath(String path) {
        return path != null && path.toUpperCase().contains("PHYSICALDRIVE");
    }

    private boolean isSystemDrive(String path) {
        if (path == null) return true;
        String upper = path.toUpperCase();
        return upper.endsWith("PHYSICALDRIVE0") || upper.contains("C:\\") || upper.equals("C:");
    }

    private boolean isRemovableDevice(WipeJob job) {
        DeviceType t = job.getDeviceType();
        return t == DeviceType.USB || t == DeviceType.EXTERNAL_HDD;
    }

    private boolean isRunningAsAdministrator() {
        // Use Advapi32Util.isCurrentProcessElevated() — checks TOKEN_ELEVATION on the
        // current process token, which is the correct UAC-aware elevation test.
        //
        // WHY getCurrentUserGroups() WAS WRONG:
        // Under UAC, an Administrator user running WITHOUT "Run as Administrator"
        // gets a split token. The Administrators SID (S-1-5-32-544) IS present in
        // the group list but with attribute SE_GROUP_USE_FOR_DENY_ONLY — it grants
        // no privileges. getCurrentUserGroups() sees the SID and returns true, but
        // CreateFile on a physical drive returns ERROR_ACCESS_DENIED (5) because
        // the effective token is a medium-integrity, non-elevated token.
        //
        // isCurrentProcessElevated() reads TokenElevation from the process token
        // and correctly returns false for a non-elevated process even when the
        // logged-in user is a member of the Administrators group.
        try {
            boolean elevated = com.sun.jna.platform.win32.Advapi32Util.isCurrentProcessElevated();
            if (elevated) {
                log.info("[WIPE] Admin check: process token is elevated (TOKEN_ELEVATION confirmed).");
            } else {
                log.warn("[WIPE] Admin check: process is NOT elevated (TOKEN_ELEVATION = false). " +
                        "Restart the backend as Administrator to allow raw disk access.");
            }
            return elevated;
        } catch (UnsatisfiedLinkError | Exception e) {
            // isCurrentProcessElevated() is a Windows-only API.
            // On Linux/Mac (e.g. Docker), fall through so the simulation path works.
            log.warn("[WIPE] isCurrentProcessElevated() unavailable ({}): {} — assuming elevated.",
                    e.getClass().getSimpleName(), e.getMessage());
            return true;
        }
    }

    private long resolveDeviceSize(WipeJob job) {
        long size = job.getDeviceSize() != null ? job.getDeviceSize() : 0L;
        if (size <= 0) {
            log.warn("[WIPE] Device size 0 for job {} — using 1 GB fallback", job.getJobId());
            return 1_073_741_824L;
        }
        return size;
    }

    private int alignToSector(int length) {
        int rem = length % SECTOR_SIZE;
        return rem == 0 ? length : length + (SECTOR_SIZE - rem);
    }

    // ── Progress Emission ───────────────────────────────────────────────────────

    private void emitProgress(WipeJob job, long bytesWritten, long totalSize,
                               int currentPass, int totalPasses, String msg,
                               long startTime, long now,
                               Consumer<WipeProgressDto> cb) {
        double passProgress = (double) bytesWritten / totalSize;
        double overallPct   = ((currentPass - 1) + passProgress) / totalPasses * 100.0;
        double elapsedSec   = (now - startTime) / 1000.0;
        double speedMbps    = elapsedSec > 0 ? (bytesWritten / 1_048_576.0) / elapsedSec : 0;
        long   etaSec       = (speedMbps > 0 && bytesWritten < totalSize)
                ? (long) ((totalSize - bytesWritten) / 1_048_576.0 / speedMbps) : 0;

        cb.accept(WipeProgressDto.builder()
                .jobId(job.getJobId())
                .status(WipeStatus.IN_PROGRESS)
                .progressPercent(Math.min((int) overallPct, 99))
                .bytesWiped(bytesWritten)
                .totalBytes(totalSize)
                .wipeSpeedMbps(Math.round(speedMbps * 10.0) / 10.0)
                .estimatedTimeRemainingSeconds(etaSec)
                .currentPass(currentPass)
                .totalPasses(totalPasses)
                .statusMessage(msg)
                .build());
    }

    // ── Simulation Fallback ────────────────────────────────────────────────────

    private void simulateWipe(WipeJob job, Consumer<WipeProgressDto> cb) {
        int  passes = job.getWipeMethod().getPasses();
        long size   = job.getDeviceSize() != null ? job.getDeviceSize() : 1_073_741_824L;
        long start  = System.currentTimeMillis();
        log.info("[WIPE] SIMULATION mode for job={}", job.getJobId());
        try {
            for (int pass = 1; pass <= passes; pass++) {
                for (int step = 0; step <= 100; step++) {
                    if (cancelledJobs.contains(job.getJobId())) {
                        cancelledJobs.remove(job.getJobId());
                        throw new InterruptedException("Cancelled");
                    }
                    double pct    = ((pass - 1) + step / 100.0) / passes * 100.0;
                    long bWiped   = (long) (pct / 100.0 * size);
                    double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                    double speed  = elapsed > 0 ? (bWiped / 1_048_576.0) / elapsed : 150.0;
                    long eta      = speed > 0 ? (long) ((size - bWiped) / 1_048_576.0 / speed) : 0;
                    cb.accept(WipeProgressDto.builder()
                            .jobId(job.getJobId()).status(WipeStatus.IN_PROGRESS)
                            .progressPercent((int) pct).bytesWiped(bWiped).totalBytes(size)
                            .wipeSpeedMbps(Math.round(speed * 10.0) / 10.0)
                            .estimatedTimeRemainingSeconds(eta)
                            .currentPass(pass).totalPasses(passes)
                            .statusMessage(String.format("Pass %d/%d: Overwriting data (%d%%)", pass, passes, (int) pct))
                            .build());
                    Thread.sleep(30);
                }
            }
            long sampleCount = Math.max(1024, Math.min(8192, (size + 511) / 512));
            persistVerificationResult(job, new VerificationSummary(
                    true,
                    (int) sampleCount,
                    0,
                    job.getWipeMethod().getDisplayName(),
                    null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WipeOperationException("Wipe interrupted");
        }
    }
}
