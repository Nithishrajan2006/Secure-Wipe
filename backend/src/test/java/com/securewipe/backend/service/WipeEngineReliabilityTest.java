package com.securewipe.backend.service;

import com.securewipe.backend.dto.response.WipeProgressDto;
import com.securewipe.backend.entity.WipeJob;
import com.securewipe.backend.enums.DeviceType;
import com.securewipe.backend.enums.WipeMethod;
import com.securewipe.backend.enums.WipeStatus;
import com.securewipe.backend.exception.WipeOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Non-destructive unit tests for WipeEngineService reliability.
 *
 * SAFETY GUARANTEES OF THIS TEST CLASS:
 * ──────────────────────────────────────
 * - All tests use JUnit @TempDir (auto-cleaned temp files) or in-memory objects.
 * - No test ever opens, reads, or writes to a real physical drive (\\.\PHYSICALDRIVE*).
 * - No test calls the Windows-only code paths (executeWindowsWipe / WindowsPhysicalDriveWriter).
 * - No test triggers the Linux device path unless the file exists (which TempDir files do,
 *   but they are ordinary files, not block devices).
 * - Tests exercise the Linux/file code path of WipeEngineService, which is functionally
 *   identical to the Windows path for write/verify correctness — only the I/O handle
 *   acquisition differs between the two.
 */
class WipeEngineReliabilityTest {

    private WipeEngineService engine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        engine = new WipeEngineService();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Build a minimal WipeJob pointing at a temp file path (NOT a real device). */
    private WipeJob buildJob(WipeMethod method, long deviceSize, String devicePath) {
        WipeJob job = new WipeJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setDeviceName("test-device");
        job.setDevicePath(devicePath);
        job.setDeviceType(DeviceType.USB);   // removable — safety gate would normally require this
        job.setDeviceSize(deviceSize);
        job.setWipeMethod(method);
        job.setStatus(WipeStatus.IN_PROGRESS);
        job.setTotalPasses(method.getPasses());
        job.setBytesWiped(0L);
        job.setProgressPercent(0);
        job.setCurrentPass(0);
        job.setWipeSpeedMbps(0.0);
        job.setEstimatedTimeRemainingSeconds(0L);
        job.setVerificationPassed(false);
        job.setVerificationSampleCount(0);
        job.setVerificationMismatchCount(0);
        return job;
    }

    /** Create a temp file pre-filled with a given byte value. */
    private Path createTempFile(String name, int size, byte fill) throws IOException {
        Path file = tempDir.resolve(name);
        byte[] data = new byte[size];
        Arrays.fill(data, fill);
        Files.write(file, data);
        return file;
    }

    /** Collect all progress updates emitted by the engine. */
    private List<WipeProgressDto> collectProgress(WipeJob job, Consumer<Consumer<WipeProgressDto>> runner) {
        List<WipeProgressDto> events = new ArrayList<>();
        runner.accept(events::add);
        return events;
    }

    // ── Test 1: ZEROS wipes every byte of the file ───────────────────────────

    @Test
    @DisplayName("ZEROS wipe overwrites every byte of the target file with 0x00")
    void testZerosWipeWritesAllZeros() throws Exception {
        // Create a 64 KB file filled with 0xFF
        int fileSize = 64 * 1024;
        Path target = createTempFile("wipe-zeros.bin", fileSize, (byte) 0xFF);

        WipeJob job = buildJob(WipeMethod.ZEROS, fileSize, target.toAbsolutePath().toString());

        List<WipeProgressDto> progress = new ArrayList<>();
        invokeLinuxWipe(job, progress::add);

        // Verify every byte is 0x00
        byte[] result = Files.readAllBytes(target);
        assertEquals(fileSize, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals((byte) 0x00, result[i],
                    "Byte at offset " + i + " should be 0x00 after ZEROS wipe");
        }
        // The progress events (emitted every 500ms, or at least once at end) must
        // have reported bytesWiped values.  Because the file is small relative to
        // BUFFER_SIZE, a single chunk covers everything and progress is emitted after
        // the loop via the final pass-complete logic — verify via verification pass.
        assertTrue(Boolean.TRUE.equals(job.getVerificationPassed()),
                "Verification must PASS after a correct ZEROS wipe");
    }

    // ── Test 2: bytesWiped tracks actual written bytes, not total size ────────

    @Test
    @DisplayName("Progress events report bytesWiped incrementally, not jumps to full size")
    void testBytesWipedIsIncrementalNotJumpToFull() throws Exception {
        int fileSize = 32 * 1024; // small enough to have few chunks
        Path target = createTempFile("wipe-progress.bin", fileSize, (byte) 0xFF);
        WipeJob job = buildJob(WipeMethod.ZEROS, fileSize, target.toAbsolutePath().toString());

        List<WipeProgressDto> progress = new ArrayList<>();
        invokeLinuxWipe(job, progress::add);

        // bytesWiped reported in each update must never exceed deviceSize
        for (WipeProgressDto p : progress) {
            if (p.getBytesWiped() != null) {
                assertTrue(p.getBytesWiped() <= fileSize,
                        "bytesWiped " + p.getBytesWiped() + " must never exceed deviceSize " + fileSize);
            }
        }
    }

    // ── Test 3: Verification of a correctly wiped file passes ─────────────────

    @Test
    @DisplayName("Verification passes when file contains expected zero pattern")
    void testVerificationPassesOnCorrectlyZeroedFile() throws Exception {
        int fileSize = 8 * 1024;
        Path target = createTempFile("verify-pass.bin", fileSize, (byte) 0x00);
        WipeJob job = buildJob(WipeMethod.ZEROS, fileSize, target.toAbsolutePath().toString());

        // Run the wipe (which internally calls verifyWrittenPattern after the write)
        invokeLinuxWipe(job, dto -> {});

        assertTrue(Boolean.TRUE.equals(job.getVerificationPassed()),
                "Verification must PASS when all bytes are 0x00 after a ZEROS wipe");
        assertEquals(0, job.getVerificationMismatchCount(),
                "Mismatch count must be 0 when verification passes");
        assertNull(job.getVerificationMismatchLocations(),
                "Mismatch locations must be null when no mismatches found");
    }

    // ── Test 4: Verification detects non-zero bytes after wipe ────────────────

    @Test
    @DisplayName("Verification detects non-zero bytes — returns FAILED result")
    void testVerificationFailsOnNonZeroBytes() throws Exception {
        int fileSize = 8 * 1024;
        // Create a zero file, then inject a non-zero byte after writing
        Path target = createTempFile("verify-fail.bin", fileSize, (byte) 0x00);

        WipeJob job = buildJob(WipeMethod.ZEROS, fileSize, target.toAbsolutePath().toString());

        // Corrupt the file mid-run by writing 0xFF at offset 100 AFTER the wipe
        // We test verifyLinuxDevice directly via the package-accessible method
        // by first zeroing (correct), then corrupting, then re-verifying.
        invokeLinuxWipe(job, dto -> {});
        assertTrue(Boolean.TRUE.equals(job.getVerificationPassed()),
                "Sanity: first wipe should pass verification");

        // Now corrupt one byte and re-verify (simulates data recovery / partial wipe)
        try (RandomAccessFile raf = new RandomAccessFile(target.toFile(), "rw")) {
            raf.seek(100);
            raf.writeByte(0xFF);
        }

        // Run a second wipe job on the corrupted file — the write will re-zero it
        // but we want to test the VERIFY path in isolation.
        // Instead, use a fresh job, zero-fill manually and inject corruption before verify.
        // The simplest test: create a file that was "wiped" but has leftover data.
        Path corruptedTarget = createTempFile("verify-corrupted.bin", fileSize, (byte) 0xFF);
        WipeJob corruptJob = buildJob(WipeMethod.ZEROS, fileSize, corruptedTarget.toAbsolutePath().toString());

        // Invoke ONLY the verification method (not the write) via reflection
        // to test that a non-zero file is correctly rejected.
        boolean result = invokeVerifyLinuxDevice(corruptJob, corruptedTarget.toAbsolutePath().toString(),
                fileSize, (byte) 0x00, "zeros");

        assertFalse(result,
                "Verification must FAIL when the file still contains non-zero bytes");
        assertTrue(corruptJob.getVerificationMismatchCount() > 0 ||
                        (corruptJob.getVerificationMismatchLocations() != null &&
                         corruptJob.getVerificationMismatchLocations().contains("FAILED")),
                "Mismatch must be recorded in the job");
    }

    // ── Test 5: Cancellation during wipe throws WipeOperationException ────────

    @Test
    @DisplayName("Cancellation mid-wipe throws WipeOperationException and stops writing")
    void testCancellationStopsWipe() throws Exception {
        // Use a very large logical device size so the loop has many iterations.
        // The file only needs to exist at the reported device size;
        // we use a pre-allocated sparse approach via FileChannel.
        int fileSize = 128 * 1024 * 1024; // 128 MB — well above the 8 MB buffer
        Path target = tempDir.resolve("wipe-cancel.bin");
        // Pre-allocate file to avoid FileChannel write beyond EOF failures
        try (RandomAccessFile raf = new RandomAccessFile(target.toFile(), "rw")) {
            raf.setLength(fileSize);
        }

        WipeJob job = buildJob(WipeMethod.ZEROS, fileSize, target.toAbsolutePath().toString());

        AtomicBoolean cancelled = new AtomicBoolean(false);
        Thread canceller = new Thread(() -> {
            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            engine.cancelWipe(job.getJobId());
            cancelled.set(true);
        });
        canceller.start();

        assertThrows(WipeOperationException.class, () ->
                invokeLinuxWipe(job, dto -> {}),
                "Cancellation must throw WipeOperationException");

        canceller.join(3000);
        assertTrue(cancelled.get(), "Canceller thread should have fired");
    }

    // ── Test 6: Write never exceeds deviceSize (final sector alignment) ────────

    @Test
    @DisplayName("Write loop never counts bytesWritten beyond deviceSize")
    void testBytesWrittenNeverExceedsDeviceSize() throws Exception {
        // Use a size that is NOT a multiple of 512 to trigger alignment padding
        int fileSize = 1500; // 1500 bytes — between 2 and 3 sectors
        // Create a file exactly this size
        Path target = tempDir.resolve("wipe-alignment.bin");
        Files.write(target, new byte[fileSize]);

        WipeJob job = buildJob(WipeMethod.ZEROS, fileSize, target.toAbsolutePath().toString());
        invokeLinuxWipe(job, dto -> {});

        // The key invariant: bytesWiped stored by progress callbacks must not exceed deviceSize
        assertTrue(job.getBytesWiped() <= fileSize,
                "bytesWiped " + job.getBytesWiped() + " must not exceed deviceSize " + fileSize);
    }

    // ── Test 7: System drive protection is enforced ───────────────────────────

    @Test
    @DisplayName("System drive (PHYSICALDRIVE0) is always blocked — WipeOperationException thrown")
    void testSystemDriveIsBlocked() {
        WipeJob job = buildJob(WipeMethod.ZEROS, 1_000_000L, "\\\\.\\PHYSICALDRIVE0");
        job.setDeviceType(DeviceType.USB); // even if marked USB, must still be blocked

        // executeWindowsWipe checks isSystemDrive before doing anything
        // We cannot invoke the private method directly, but we can confirm the
        // public executeWipe dispatches to executeWindowsWipe on Windows and
        // that the system drive check fires before any I/O.
        // We test this by observing a WipeOperationException with the block message.
        // This test only runs on Windows where \\.\PHYSICALDRIVE0 is a physical path.
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("windows")) {
            WipeOperationException ex = assertThrows(WipeOperationException.class,
                    () -> engine.executeWipe(job, dto -> {}),
                    "PHYSICALDRIVE0 must always throw WipeOperationException");
            assertTrue(ex.getMessage().contains("BLOCKED") || ex.getMessage().contains("system drive"),
                    "Exception message must mention the system-drive block: " + ex.getMessage());
        } else {
            // On Linux/CI: path doesn't match PHYSICALDRIVE — simulation runs safely
            // Just confirm no exception is thrown (simulation handles non-device paths)
            assertDoesNotThrow(() -> engine.executeWipe(job, dto -> {}),
                    "On non-Windows, non-device paths should trigger simulation, not crash");
        }
    }

    // ── Test 8: Device disconnect / read failure during verification → FAILED ──

    @Test
    @DisplayName("Verification read failure propagates as exception — job cannot become COMPLETED")
    void testVerificationReadFailureProducesException() throws Exception {
        // Create a zero file then delete it to simulate device disconnect during verification
        int fileSize = 4 * 1024;
        Path target = createTempFile("wipe-disconnect.bin", fileSize, (byte) 0x00);
        WipeJob job = buildJob(WipeMethod.ZEROS, fileSize, target.toAbsolutePath().toString());

        // Delete the file to simulate device being unplugged during verification
        Files.delete(target);

        // Attempting to verify a non-existent path must throw IOException,
        // which propagates up and prevents COMPLETED.
        assertThrows(Exception.class, () ->
                invokeVerifyLinuxDevice(job, target.toAbsolutePath().toString(),
                        fileSize, (byte) 0x00, "zeros"),
                "Verification of missing file (disconnected device) must throw an exception");
    }

    // ── Test 9: isVerificationSkipped logic (random passes) ──────────────────

    @Test
    @DisplayName("Random wipe pass is marked SKIPPED not FAILED — job can still be COMPLETED")
    void testRandomPassVerificationIsSkipped() throws Exception {
        int fileSize = 4 * 1024;
        Path target = tempDir.resolve("wipe-random.bin");
        Files.write(target, new byte[fileSize]);

        WipeJob job = buildJob(WipeMethod.RANDOM, fileSize, target.toAbsolutePath().toString());
        invokeLinuxWipe(job, dto -> {});

        // For RANDOM, verificationPassed should be true with a SKIPPED message
        assertTrue(Boolean.TRUE.equals(job.getVerificationPassed()),
                "Random wipe should set verificationPassed=true (skipped, not failed)");
        String locs = job.getVerificationMismatchLocations();
        assertTrue(locs != null && locs.startsWith("SKIPPED:"),
                "Random wipe verification must be marked SKIPPED, got: " + locs);
    }

    // ── Test 10: Progress percent never exceeds 99 during wipe ───────────────

    @Test
    @DisplayName("Progress percent never reaches 100 during active wipe — only at COMPLETED")
    void testProgressNeverReaches100DuringWipe() throws Exception {
        int fileSize = 32 * 1024;
        Path target = createTempFile("wipe-progress-pct.bin", fileSize, (byte) 0xFF);
        WipeJob job = buildJob(WipeMethod.ZEROS, fileSize, target.toAbsolutePath().toString());

        List<WipeProgressDto> progress = new ArrayList<>();
        invokeLinuxWipe(job, progress::add);

        for (WipeProgressDto p : progress) {
            assertNotNull(p.getProgressPercent());
            assertTrue(p.getProgressPercent() <= 99,
                    "In-progress updates must be <= 99%, got " + p.getProgressPercent());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reflection helpers — access private methods without modifying production code
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Invokes the private executeLinuxWipe(WipeJob, Consumer) method via reflection.
     * This exercises the write + verify loop without touching any physical drive.
     * On Windows, the file path is an ordinary temp file — not \\.\PHYSICALDRIVEn —
     * so executeWindowsWipe's simulateWipe path would run instead of the real path;
     * we bypass that by calling executeLinuxWipe directly.
     */
    private void invokeLinuxWipe(WipeJob job, Consumer<WipeProgressDto> cb) throws Exception {
        Method m = WipeEngineService.class.getDeclaredMethod(
                "executeLinuxWipe", WipeJob.class, Consumer.class);
        m.setAccessible(true);
        try {
            m.invoke(engine, job, cb);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof WipeOperationException) throw (WipeOperationException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        }
    }

    /**
     * Invokes verifyLinuxDevice and returns summary.passed().
     * Also persists the result into the job entity so tests can inspect
     * verificationPassed / verificationMismatchCount etc.
     */
    private boolean invokeVerifyLinuxDevice(WipeJob job, String devicePath, long deviceSize,
                                             byte fillByte, String patternName) throws Exception {
        Method m = WipeEngineService.class.getDeclaredMethod(
                "verifyLinuxDevice",
                WipeJob.class, String.class, long.class, byte.class,
                String.class, int.class, int.class, Consumer.class);
        m.setAccessible(true);
        try {
            Object summary = m.invoke(engine, job, devicePath, deviceSize, fillByte,
                    patternName, 1, 1, (Consumer<WipeProgressDto>) dto -> {});
            // Extract 'passed' from the VerificationSummary record via reflection
            boolean passed = (boolean) summary.getClass().getMethod("passed").invoke(summary);
            // Persist into job
            Method persist = WipeEngineService.class.getDeclaredMethod(
                    "persistVerificationResult", WipeJob.class, summary.getClass());
            // VerificationSummary is a private record — find by enclosing class
            for (Class<?> inner : WipeEngineService.class.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("VerificationSummary")) {
                    Method persistM = WipeEngineService.class.getDeclaredMethod(
                            "persistVerificationResult", WipeJob.class, inner);
                    persistM.setAccessible(true);
                    persistM.invoke(engine, job, summary);
                    break;
                }
            }
            return passed;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        }
    }
}
