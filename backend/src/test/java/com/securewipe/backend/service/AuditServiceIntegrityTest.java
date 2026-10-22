package com.securewipe.backend.service;

import com.securewipe.backend.dto.response.AuditIntegrityDto;
import com.securewipe.backend.entity.AuditLog;
import com.securewipe.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceIntegrityTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void verifyAuditIntegrityReturnsValidWhenChainMatches() throws Exception {
        AuditLog first = new AuditLog();
        first.setId(1L);
        first.setUsername("admin");
        first.setAction("LOGIN");
        first.setResourceType("USER");
        first.setResourceId("1");
        first.setDetails("login");
        first.setIpAddress("127.0.0.1");
        first.setUserAgent("test-agent");
        first.setStatus("SUCCESS");
        first.setCreatedAt(LocalDateTime.of(2026, 8, 4, 20, 0, 0));
        first.setPreviousHash(null);
        first.setEntryHash(computeEntryHash(first, null));

        AuditLog second = new AuditLog();
        second.setId(2L);
        second.setUsername("admin");
        second.setAction("WIPE_STARTED");
        second.setResourceType("WIPE_JOB");
        second.setResourceId("42");
        second.setDetails("wipe started");
        second.setIpAddress("127.0.0.1");
        second.setUserAgent("test-agent");
        second.setStatus("SUCCESS");
        second.setCreatedAt(LocalDateTime.of(2026, 8, 4, 20, 1, 0));
        second.setPreviousHash(first.getEntryHash());
        second.setEntryHash(computeEntryHash(second, first.getEntryHash()));

        when(auditLogRepository.findAllByOrderByIdAsc()).thenReturn(List.of(first, second));

        AuditIntegrityDto result = auditService.verifyAuditIntegrity();

        assertTrue(result.isValid());
        assertEquals(2, result.getCheckedCount());
    }

    private String computeEntryHash(AuditLog log, String previousHash) throws Exception {
        String payload = String.join("|",
                log.getUsername() != null ? log.getUsername() : "",
                log.getAction() != null ? log.getAction() : "",
                log.getResourceType() != null ? log.getResourceType() : "",
                log.getResourceId() != null ? log.getResourceId() : "",
                log.getDetails() != null ? log.getDetails() : "",
                log.getIpAddress() != null ? log.getIpAddress() : "",
                log.getUserAgent() != null ? log.getUserAgent() : "",
                log.getStatus() != null ? log.getStatus() : "",
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                previousHash != null ? previousHash : ""
        );

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
