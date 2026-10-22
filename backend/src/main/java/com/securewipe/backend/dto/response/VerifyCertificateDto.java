package com.securewipe.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned by GET /api/reports/verify/{certificateId}.
 * This endpoint is public — no authentication required — so anyone
 * (including QR-code scanners) can verify a wipe certificate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyCertificateDto {

    /** Whether the certificate was found in the database. */
    private boolean valid;

    // ── Certificate identity ────────────────────────────────────────────────
    private String certificateId;
    private String jobId;

    // ── Device information ──────────────────────────────────────────────────
    private String deviceName;
    private String deviceModel;
    private String deviceType;
    private String devicePath;
    private String capacity;
    private String serialNumber;
    private String fileSystem;

    // ── Wipe operation details ──────────────────────────────────────────────
    private String wipeMethod;
    private String algorithm;
    private String operator;
    private String status;
    private String startedAt;
    private String completedAt;
    private String duration;

    // ── Certificate metadata ────────────────────────────────────────────────
    private String issuedAt;
    private String sha256Hash;
}
