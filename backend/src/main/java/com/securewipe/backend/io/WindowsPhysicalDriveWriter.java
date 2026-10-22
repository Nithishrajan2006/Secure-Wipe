package com.securewipe.backend.io;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens a Windows physical drive (\\.\PHYSICALDRIVEn or \\?\PHYSICALDRIVEn)
 * directly via the Win32 API (CreateFile / WriteFile / FlushFileBuffers /
 * CloseHandle) using JNA, bypassing java.io entirely for the open step.
 *
 * WHY THIS CLASS EXISTS
 * ──────────────────────────────────────────────────────────────────────────
 * Java's RandomAccessFile / FileChannel call Win32 CreateFile() through the
 * JVM's internal native I/O layer. That layer is written for ordinary files
 * and unconditionally passes FILE_ATTRIBUTE_NORMAL as dwFlagsAndAttributes.
 * There is no public java.io/java.nio option to change this.
 *
 * Physical drive paths are device objects, not files. CreateFile() rejects
 * FILE_ATTRIBUTE_NORMAL for device paths and returns ERROR_INVALID_PARAMETER
 * (Win32 error 87) — surfaced by the JVM as:
 *   "FileNotFoundException: \\?\PHYSICALDRIVEn (The parameter is incorrect)"
 * This class makes the CreateFile call itself (dwFlagsAndAttributes = 0, not
 * FILE_ATTRIBUTE_NORMAL) and then drives WriteFile / FlushFileBuffers /
 * CloseHandle directly against the resulting HANDLE.
 *
 * WHY WriteFile CAN STILL FAIL WITH ERROR_ACCESS_DENIED (5)
 * ──────────────────────────────────────────────────────────────────────────
 * A valid physical-drive HANDLE is not sufficient on its own. If Windows has
 * a filesystem mounted anywhere on that disk (a drive letter), the volume
 * manager still considers the disk "in use" and the OS itself — not any
 * user-mode program — blocks raw writes to it with ERROR_ACCESS_DENIED, even
 * though CreateFile succeeded and even though the caller is elevated.
 *
 * The fix is to explicitly take ownership of every mounted volume that lives
 * on the target disk before writing:
 *   1. Enumerate drive letters A:–Z:, open each with CreateFile, and use
 *      IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS to check which physical disk it
 *      actually sits on.
 *   2. For each volume that resides on the target disk, send
 *      FSCTL_LOCK_VOLUME (claims exclusive access — fails safely if some
 *      other process, e.g. Explorer or an antivirus scanner, has a file
 *      open on it) and then FSCTL_DISMOUNT_VOLUME (releases the volume's
 *      claim on the underlying disk).
 *   3. Keep those volume handles OPEN for the lifetime of this writer —
 *      the lock is only held as long as the handle that acquired it stays
 *      open. Closing early re-mounts the volume and re-blocks raw writes.
 *   4. On close(), send FSCTL_UNLOCK_VOLUME and close each volume handle,
 *      then close the physical-drive handle.
 *
 * Once the HANDLE is valid and all volumes on the disk are locked/dismounted,
 * WriteFile behaves like a normal sequential append — no manual
 * SetFilePointerEx calls are needed as long as writes happen in order from
 * offset 0, which is how WipeEngineService uses it.
 */
@Slf4j
public final class WindowsPhysicalDriveWriter implements AutoCloseable {

    // ── FSCTL / IOCTL codes (stable Win32 constants — defined locally so this
    //    class has no dependency on a specific jna-platform version exposing
    //    them under a particular constant name). ────────────────────────────
    private static final int FSCTL_LOCK_VOLUME                       = 0x00090018;
    private static final int FSCTL_UNLOCK_VOLUME                     = 0x0009001C;
    private static final int FSCTL_DISMOUNT_VOLUME                   = 0x00090020;
    private static final int IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS    = 0x00560000;

    private final WinNT.HANDLE handle;
    private final List<WinNT.HANDLE> lockedVolumeHandles = new ArrayList<>();
    private long position = 0L;

    /**
     * Opens the given device path for raw read/write access. Before opening
     * the physical drive itself, locates every mounted volume on the same
     * disk and locks + dismounts it, so the subsequent WriteFile calls are
     * not rejected with ERROR_ACCESS_DENIED by the volume manager.
     *
     * @param devicePath e.g. "\\\\.\\PHYSICALDRIVE1" or "\\\\?\\PHYSICALDRIVE1"
     * @throws IOException if CreateFile fails (wrong path, drive in use,
     *                      insufficient privileges, etc.) — the Win32 error
     *                      code is included in the message for diagnostics.
     */
    public WindowsPhysicalDriveWriter(String devicePath) throws IOException {

        int driveNumber = extractDriveNumber(devicePath);
        if (driveNumber >= 0) {
            lockAndDismountVolumesOnDrive(driveNumber);
        } else {
            log.warn("[WIPE] Could not parse a PHYSICALDRIVE number from \"{}\" — " +
                    "skipping volume lock/dismount step. WriteFile may fail with " +
                    "ACCESS_DENIED if a filesystem is mounted on this disk.", devicePath);
        }

        WinNT.HANDLE h = Kernel32.INSTANCE.CreateFile(
                devicePath,
                WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE,
                null,
                WinNT.OPEN_EXISTING,
                0,                  // <-- NOT FILE_ATTRIBUTE_NORMAL. Required for device paths.
                null
        );

        if (h == null || WinBase.INVALID_HANDLE_VALUE.equals(h)) {
            int err = Kernel32.INSTANCE.GetLastError();
            unlockAndCloseVolumes(); // don't leak locked volume handles if this fails
            throw new IOException("CreateFile failed for " + devicePath +
                    " — Win32 error code " + err + ". Common causes: not running " +
                    "as Administrator, the drive is in use by another process, " +
                    "or the drive number is wrong.");
        }

        this.handle = h;
    }

    /**
     * Opens the device for READ-ONLY access. Does NOT lock or dismount volumes —
     * read-only access does not require exclusive volume ownership.
     * Used by the post-wipe verification reader.
     *
     * @param devicePath e.g. "\\\\.\\PHYSICALDRIVE1"
     * @throws IOException if the device cannot be opened for reading
     */
    public static WindowsPhysicalDriveWriter openReadOnly(String devicePath) throws IOException {
        return new WindowsPhysicalDriveWriter(devicePath, true);
    }

    /** Internal constructor for read-only mode — skips volume lock/dismount. */
    private WindowsPhysicalDriveWriter(String devicePath, boolean readOnly) throws IOException {
        // readOnly = true: open with GENERIC_READ only, no locking needed
        WinNT.HANDLE h = Kernel32.INSTANCE.CreateFile(
                devicePath,
                WinNT.GENERIC_READ,
                WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE,
                null,
                WinNT.OPEN_EXISTING,
                0,
                null
        );
        if (h == null || WinBase.INVALID_HANDLE_VALUE.equals(h)) {
            int err = Kernel32.INSTANCE.GetLastError();
            throw new IOException("ReadOnly CreateFile failed for " + devicePath +
                    " — Win32 error code " + err);
        }
        this.handle = h;
    }

    /**
     * Writes exactly {@code length} bytes from {@code buffer} at the current
     * file position, then advances the position. Mirrors
     * FileChannel.write(ByteBuffer) semantics for sequential writers.
     */
    public void write(byte[] buffer, int length) throws IOException {
        IntByReference written = new IntByReference(0);
        boolean ok = Kernel32.INSTANCE.WriteFile(handle, buffer, length, written, null);

        if (!ok) {
            int err = Kernel32.INSTANCE.GetLastError();
            throw new IOException("WriteFile failed at offset " + position +
                    " (length=" + length + ") — Win32 error code " + err +
                    (err == 5 ? " (ACCESS_DENIED — a volume on this disk may not have " +
                            "been successfully locked/dismounted; check earlier log lines)" : ""));
        }
        if (written.getValue() != length) {
            throw new IOException("Partial write at offset " + position +
                    ": requested " + length + " bytes, wrote " + written.getValue() +
                    ". Physical drive writes must be sector-aligned.");
        }
        position += written.getValue();
    }

    /**
     * Reads exactly {@code length} bytes into {@code buffer} at the current file
     * position, then advances the position. Returns the number of bytes actually read
     * (may be less than {@code length} at end of device — caller must handle this).
     *
     * Used by the post-wipe verification reader to re-read the physical drive via
     * the same JNA path that bypasses Java's FILE_ATTRIBUTE_NORMAL restriction.
     */
    public int read(byte[] buffer, int length) throws IOException {
        IntByReference bytesRead = new IntByReference(0);
        boolean ok = Kernel32.INSTANCE.ReadFile(handle, buffer, length, bytesRead, null);
        if (!ok) {
            int err = Kernel32.INSTANCE.GetLastError();
            // ERROR_HANDLE_EOF (38) is expected at the very end of the device — not an error.
            if (err == 38) {
                return bytesRead.getValue();
            }
            throw new IOException("ReadFile failed at offset " + position +
                    " — Win32 error code " + err);
        }
        int n = bytesRead.getValue();
        position += n;
        return n;
    }

    /** Equivalent of FileChannel.force(true) — flushes the OS write cache to physical media. */
    public void flush() throws IOException {
        if (!Kernel32.INSTANCE.FlushFileBuffers(handle)) {
            int err = Kernel32.INSTANCE.GetLastError();
            throw new IOException("FlushFileBuffers failed — Win32 error code " + err);
        }
    }

    @Override
    public void close() {
        if (handle != null && !WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
        unlockAndCloseVolumes();
    }

    // ── Volume locking / dismounting ─────────────────────────────────────────

    /**
     * Enumerates drive letters A:–Z:, identifies which ones (if any) reside
     * on {@code targetDriveNumber}, and locks + dismounts each one found.
     * Locked volume handles are kept open in {@link #lockedVolumeHandles}
     * until {@link #close()} is called — closing the handle early releases
     * the lock and re-mounts the volume.
     */
    private void lockAndDismountVolumesOnDrive(int targetDriveNumber) {
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            String volumePath = "\\\\.\\" + letter + ":";

            WinNT.HANDLE volHandle = Kernel32.INSTANCE.CreateFile(
                    volumePath,
                    WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                    WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE,
                    null,
                    WinNT.OPEN_EXISTING,
                    0,
                    null
            );

            if (volHandle == null || WinBase.INVALID_HANDLE_VALUE.equals(volHandle)) {
                log.debug("[WIPE] Drive letter {} — no such volume / not accessible, skipping.", volumePath);
                continue; // no such drive letter, or not accessible — nothing to do
            }

            int detectedDiskNumber;
            try {
                detectedDiskNumber = queryDiskNumber(volHandle);
            } catch (Exception e) {
                log.debug("[WIPE] Drive letter {} — could not query disk extents: {}",
                        volumePath, e.getMessage());
                detectedDiskNumber = -1;
            }

            log.info("[WIPE] Checked drive letter {} — resolved to PHYSICALDRIVE{} " +
                            "(target is PHYSICALDRIVE{})",
                    volumePath, detectedDiskNumber, targetDriveNumber);

            if (detectedDiskNumber != targetDriveNumber) {
                Kernel32.INSTANCE.CloseHandle(volHandle);
                continue;
            }

            log.info("[WIPE] Volume {} resides on PHYSICALDRIVE{} — locking and dismounting...",
                    volumePath, targetDriveNumber);

            IntByReference bytesReturned = new IntByReference();

            boolean locked = Kernel32.INSTANCE.DeviceIoControl(
                    volHandle, FSCTL_LOCK_VOLUME, null, 0, null, 0, bytesReturned, null);
            if (!locked) {
                int err = Kernel32.INSTANCE.GetLastError();
                log.warn("[WIPE] FSCTL_LOCK_VOLUME FAILED for {} — Win32 error {}. " +
                                "Proceeding anyway; a process with an open handle on this " +
                                "volume (e.g. Explorer, an AV scanner) can cause this. " +
                                "WriteFile may still fail with ACCESS_DENIED.",
                        volumePath, err);
            } else {
                log.info("[WIPE] FSCTL_LOCK_VOLUME succeeded for {}.", volumePath);
            }

            boolean dismounted = Kernel32.INSTANCE.DeviceIoControl(
                    volHandle, FSCTL_DISMOUNT_VOLUME, null, 0, null, 0, bytesReturned, null);
            if (!dismounted) {
                int err = Kernel32.INSTANCE.GetLastError();
                log.warn("[WIPE] FSCTL_DISMOUNT_VOLUME FAILED for {} — Win32 error {}", volumePath, err);
            } else {
                log.info("[WIPE] FSCTL_DISMOUNT_VOLUME succeeded for {}.", volumePath);
            }

            // The lock is only held while this handle stays open — do not close it here.
            lockedVolumeHandles.add(volHandle);
        }

        if (lockedVolumeHandles.isEmpty()) {
            log.info("[WIPE] No mounted volumes found on PHYSICALDRIVE{} " +
                    "(unpartitioned disk, or no drive letter assigned).", targetDriveNumber);
        }
    }

    /**
     * Output layout (VOLUME_DISK_EXTENTS):
     *   DWORD NumberOfDiskExtents;                  // offset 0, padded to 8 bytes
     *   DISK_EXTENT Extents[N];                      // starts at offset 8
     *     each DISK_EXTENT is 24 bytes:
     *       DWORD DiskNumber        (4 bytes, padded to 8 for alignment)
     *       LARGE_INTEGER StartingOffset (8 bytes)
     *       LARGE_INTEGER ExtentLength   (8 bytes)
     *
     * Uses IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS to determine which physical
     * disk number the given volume handle's underlying storage lives on.
     * Returns -1 if it can't be determined (e.g. removable media reader with
     * no extents, network drive, etc.), or if the volume spans multiple disks
     * (logged at debug level; only the first extent's disk number is used —
     * spanned/striped volumes are not expected on the target removable disks
     * this tool operates on).
     */
    private int queryDiskNumber(WinNT.HANDLE volHandle) {
        final int bufferSize = 1024; // comfortably fits many extents; one is typical
        Memory outBuffer = new Memory(bufferSize);
        IntByReference bytesReturned = new IntByReference();

        boolean ok = Kernel32.INSTANCE.DeviceIoControl(
                volHandle, IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS,
                null, 0, outBuffer, bufferSize, bytesReturned, null);

        if (!ok) {
            return -1;
        }

        int extentCount = outBuffer.getInt(0);
        if (extentCount <= 0) {
            return -1;
        }
        if (extentCount > 1) {
            log.debug("[WIPE] Volume spans {} extents — using first extent's disk number only.",
                    extentCount);
        }
        return outBuffer.getInt(8L); // first DISK_EXTENT.DiskNumber
    }

    /** Sends FSCTL_UNLOCK_VOLUME and closes every volume handle acquired in the constructor. */
    private void unlockAndCloseVolumes() {
        for (WinNT.HANDLE volHandle : lockedVolumeHandles) {
            IntByReference bytesReturned = new IntByReference();
            Kernel32.INSTANCE.DeviceIoControl(
                    volHandle, FSCTL_UNLOCK_VOLUME, null, 0, null, 0, bytesReturned, null);
            Kernel32.INSTANCE.CloseHandle(volHandle);
        }
        lockedVolumeHandles.clear();
    }

    /** Parses the numeric suffix from a "...PHYSICALDRIVEn..." path. Returns -1 if not found. */
    private static int extractDriveNumber(String devicePath) {
        if (devicePath == null) return -1;
        String upper = devicePath.toUpperCase();
        int idx = upper.indexOf("PHYSICALDRIVE");
        if (idx < 0) return -1;
        String digits = upper.substring(idx + "PHYSICALDRIVE".length()).replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
