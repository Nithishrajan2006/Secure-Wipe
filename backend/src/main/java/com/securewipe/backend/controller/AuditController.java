package com.securewipe.backend.controller;

import com.securewipe.backend.dto.response.ApiResponse;
import com.securewipe.backend.dto.response.AuditIntegrityDto;
import com.securewipe.backend.dto.response.AuditLogDto;
import com.securewipe.backend.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "View and manage audit logs (Admin only)")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all audit logs with optional filters (Admin only)")
    public ResponseEntity<ApiResponse<Page<AuditLogDto>>> getAuditLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AuditLogDto> logs = auditService.getAuditLogs(userId, action, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @GetMapping("/integrity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verify audit log integrity chain (Admin only)")
    public ResponseEntity<ApiResponse<AuditIntegrityDto>> verifyAuditIntegrity() {
        return ResponseEntity.ok(ApiResponse.success(auditService.verifyAuditIntegrity()));
    }
}
