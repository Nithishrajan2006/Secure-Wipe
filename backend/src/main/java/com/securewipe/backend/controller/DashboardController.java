package com.securewipe.backend.controller;

import com.securewipe.backend.dto.response.ApiResponse;
import com.securewipe.backend.dto.response.DashboardStatsDto;
import com.securewipe.backend.entity.User;
import com.securewipe.backend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard statistics and analytics")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @Operation(summary = "Get dashboard statistics")
    public ResponseEntity<ApiResponse<DashboardStatsDto>> getDashboardStats(
            @AuthenticationPrincipal User currentUser) {
        DashboardStatsDto stats = dashboardService.getStats(currentUser);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
