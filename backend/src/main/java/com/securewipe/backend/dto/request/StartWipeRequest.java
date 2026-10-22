package com.securewipe.backend.dto.request;

import com.securewipe.backend.enums.DeviceType;
import com.securewipe.backend.enums.WipeMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartWipeRequest {

    @NotBlank(message = "Device path is required")
    private String devicePath;

    @NotBlank(message = "Device name is required")
    private String deviceName;

    @NotNull(message = "Device type is required")
    private DeviceType deviceType;

    @NotNull(message = "Wipe method is required")
    private WipeMethod wipeMethod;

    private Long deviceSize;
    private String fileSystem;
    private String mountPoint;

    // Confirmation token - must match device name for safety
    @NotBlank(message = "Confirmation is required")
    private String confirmationText;
}
