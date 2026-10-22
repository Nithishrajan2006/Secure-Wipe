package com.securewipe.backend.controller;

import com.securewipe.backend.dto.request.StartWipeRequest;
import com.securewipe.backend.dto.response.ApiResponse;
import com.securewipe.backend.dto.response.WipeJobDto;
import com.securewipe.backend.entity.User;
import com.securewipe.backend.enums.WipeStatus;
import com.securewipe.backend.service.WipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/wipe")
@RequiredArgsConstructor
@Tag(name = "Wipe Operations", description = "Manage secure data wiping operations")
public class WipeController {

    private final WipeService wipeService;

    @PostMapping("/start")
    @Operation(summary = "Start a secure wipe operation")
    public ResponseEntity<ApiResponse<WipeJobDto>> startWipe(
            @Valid @RequestBody StartWipeRequest request,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest httpRequest) {
        WipeJobDto job = wipeService.startWipe(request, currentUser, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Wipe operation started", job));
    }

    @GetMapping("/progress/{jobId}")
    @Operation(summary = "Get current progress of a wipe job")
    public ResponseEntity<ApiResponse<WipeJobDto>> getWipeProgress(
            @PathVariable String jobId) {
        WipeJobDto job = wipeService.getWipeProgress(jobId);
        return ResponseEntity.ok(ApiResponse.success(job));
    }

    @PostMapping("/cancel/{jobId}")
    @Operation(summary = "Cancel an ongoing wipe operation")
    public ResponseEntity<ApiResponse<WipeJobDto>> cancelWipe(
            @PathVariable String jobId,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest httpRequest) {
        WipeJobDto job = wipeService.cancelWipe(jobId, currentUser, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Cancellation requested", job));
    }

    @GetMapping("/history")
    @Operation(summary = "Get wipe history with optional filters")
    public ResponseEntity<ApiResponse<Page<WipeJobDto>>> getWipeHistory(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) WipeStatus status,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<WipeJobDto> history = wipeService.getWipeHistory(currentUser, status, method, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get a specific wipe job by ID")
    public ResponseEntity<ApiResponse<WipeJobDto>> getWipeJob(
            @PathVariable String jobId) {
        WipeJobDto job = wipeService.getWipeProgress(jobId);
        return ResponseEntity.ok(ApiResponse.success(job));
    }
}
