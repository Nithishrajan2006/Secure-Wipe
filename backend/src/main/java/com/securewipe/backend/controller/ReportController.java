package com.securewipe.backend.controller;

import com.securewipe.backend.dto.response.ApiResponse;
import com.securewipe.backend.dto.response.VerifyCertificateDto;
import com.securewipe.backend.entity.AuditLog;
import com.securewipe.backend.service.AuditService;
import com.securewipe.backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Generate PDF certificates and export audit logs")
public class ReportController {

    private final ReportService reportService;
    private final AuditService auditService;

    @GetMapping("/certificate/{jobId}")
    @Operation(summary = "Download PDF wipe certificate for a completed job")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable String jobId) {
        byte[] pdf = reportService.generateWipeCertificate(jobId);
        String filename = "wipe-certificate-" + jobId.substring(0, 8) + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    /**
     * Public endpoint — no authentication required.
     * Called by the VerifyPage when a user scans the QR code on a certificate.
     */
    @GetMapping("/verify/{certificateId}")
    @Operation(summary = "Verify a wipe certificate by its certificate ID (public)")
    public ResponseEntity<ApiResponse<VerifyCertificateDto>> verifyCertificate(
            @PathVariable String certificateId) {
        VerifyCertificateDto result = reportService.verifyCertificate(certificateId);
        if (result.isValid()) {
            return ResponseEntity.ok(ApiResponse.success("Certificate verified", result));
        }
        return ResponseEntity.ok(ApiResponse.success("Certificate not found", result));
    }

    @GetMapping("/audit/csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Export audit logs as CSV (Admin only)")
    public ResponseEntity<byte[]> exportAuditCsv(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        List<AuditLog> logs = auditService.getAuditLogsForExport(userId, action, from, to);
        byte[] csv = reportService.exportAuditLogsCsv(logs);
        String filename = "audit-log-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }

    @GetMapping("/audit/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Export audit logs as PDF (Admin only)")
    public ResponseEntity<byte[]> exportAuditPdf(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        List<AuditLog> logs = auditService.getAuditLogsForExport(userId, action, from, to);
        byte[] pdf = reportService.exportAuditLogsPdf(logs);
        String filename = "audit-log-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }
}
