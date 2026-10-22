package com.securewipe.backend.service;

import com.securewipe.backend.dto.response.AuditIntegrityDto;
import com.securewipe.backend.dto.response.AuditLogDto;
import com.securewipe.backend.entity.AuditLog;
import com.securewipe.backend.entity.User;
import com.securewipe.backend.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional
    public void log(User user, String action, String resourceType,
                    String resourceId, String details, HttpServletRequest request) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .username(user != null ? user.getUsername() : "system")
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .details(details)
                    .ipAddress(request != null ? getClientIp(request) : null)
                    .userAgent(request != null ? request.getHeader("User-Agent") : null)
                    .status("SUCCESS")
                    .build();
            applyIntegrityMetadata(auditLog);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    @Async
    @Transactional
    public void logError(User user, String action, String resourceType,
                         String resourceId, String details, String errorMessage,
                         HttpServletRequest request) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .username(user != null ? user.getUsername() : "system")
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .details(details + " | Error: " + errorMessage)
                    .ipAddress(request != null ? getClientIp(request) : null)
                    .userAgent(request != null ? request.getHeader("User-Agent") : null)
                    .status("FAILED")
                    .build();
            applyIntegrityMetadata(auditLog);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save error audit log: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogs(Long userId, String action,
                                           LocalDateTime from, LocalDateTime to,
                                           Pageable pageable) {
        return auditLogRepository.findByFilters(userId, action, from, to, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsForExport(Long userId, String action,
                                                  LocalDateTime from, LocalDateTime to) {
        return auditLogRepository.findAllForExport(userId, action, from, to);
    }

    @Transactional(readOnly = true)
    public AuditIntegrityDto verifyAuditIntegrity() {
        List<AuditLog> logs = auditLogRepository.findAllByOrderByIdAsc();
        String previousHash = null;

        for (int index = 0; index < logs.size(); index++) {
            AuditLog log = logs.get(index);
            String expectedEntryHash = computeEntryHash(log, previousHash);
            String expectedPreviousHash = index == 0 ? null : logs.get(index - 1).getEntryHash();

            if (!Objects.equals(log.getPreviousHash(), expectedPreviousHash)
                    || !Objects.equals(log.getEntryHash(), expectedEntryHash)) {
                return AuditIntegrityDto.builder()
                        .valid(false)
                        .checkedCount(logs.size())
                        .firstBrokenRecordId(log.getId())
                        .message("Tamper evidence mismatch detected at audit log record " + log.getId())
                        .build();
            }
            previousHash = log.getEntryHash();
        }

        return AuditIntegrityDto.builder()
                .valid(true)
                .checkedCount(logs.size())
                .message("Audit log chain verified successfully")
                .build();
    }

    private AuditLogDto toDto(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .username(log.getUsername())
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .status(log.getStatus())
                .previousHash(log.getPreviousHash())
                .entryHash(log.getEntryHash())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private void applyIntegrityMetadata(AuditLog auditLog) {
        AuditLog latest = auditLogRepository.findTopByOrderByIdDesc();
        auditLog.setCreatedAt(LocalDateTime.now());
        auditLog.setPreviousHash(latest != null ? latest.getEntryHash() : null);
        auditLog.setEntryHash(computeEntryHash(auditLog, auditLog.getPreviousHash()));
    }

    private String computeEntryHash(AuditLog auditLog, String previousHash) {
        String payload = String.join("|",
                auditLog.getUsername() != null ? auditLog.getUsername() : "",
                auditLog.getAction() != null ? auditLog.getAction() : "",
                auditLog.getResourceType() != null ? auditLog.getResourceType() : "",
                auditLog.getResourceId() != null ? auditLog.getResourceId() : "",
                auditLog.getDetails() != null ? auditLog.getDetails() : "",
                auditLog.getIpAddress() != null ? auditLog.getIpAddress() : "",
                auditLog.getUserAgent() != null ? auditLog.getUserAgent() : "",
                auditLog.getStatus() != null ? auditLog.getStatus() : "",
                auditLog.getCreatedAt() != null ? auditLog.getCreatedAt().toString() : "",
                previousHash != null ? previousHash : ""
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute audit log hash", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
