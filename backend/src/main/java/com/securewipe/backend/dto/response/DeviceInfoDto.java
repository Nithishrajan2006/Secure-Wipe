package com.securewipe.backend.dto.response;

import com.securewipe.backend.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfoDto {
    private String id;
    private String name;
    private String model;
    private String path;
    private Long size;
    private String sizeFormatted;
    private DeviceType type;
    private String fileSystem;
    private String mountPoint;
    private String serialNumber;
    private String firmwareVersion;
    private boolean removable;
    private List<PartitionDto> partitions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartitionDto {
        private String name;
        private String mountPoint;
        private String fileSystem;
        private Long size;
        private String sizeFormatted;
        private String path;
        private DeviceType type;
    }
}
