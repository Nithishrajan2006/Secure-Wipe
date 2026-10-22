package com.securewipe.backend.controller;

import com.securewipe.backend.dto.response.ApiResponse;
import com.securewipe.backend.dto.response.DeviceInfoDto;
import com.securewipe.backend.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "Device Management", description = "Detect and list storage devices")
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    @Operation(summary = "Get all detected storage devices")
    public ResponseEntity<ApiResponse<List<DeviceInfoDto>>> getAllDevices() {
        List<DeviceInfoDto> devices = deviceService.getAllDevices();
        return ResponseEntity.ok(ApiResponse.success(devices));
    }

    @GetMapping("/refresh")
    @Operation(summary = "Refresh and re-detect storage devices")
    public ResponseEntity<ApiResponse<List<DeviceInfoDto>>> refreshDevices() {
        List<DeviceInfoDto> devices = deviceService.getAllDevices();
        return ResponseEntity.ok(ApiResponse.success("Devices refreshed", devices));
    }
}
