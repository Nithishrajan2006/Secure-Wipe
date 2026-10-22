package com.securewipe.backend.service;

import com.securewipe.backend.dto.response.DeviceInfoDto;
import com.securewipe.backend.enums.DeviceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HWPartition;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class DeviceService {

    public List<DeviceInfoDto> getAllDevices() {
        List<DeviceInfoDto> devices = new ArrayList<>();
        try {
            SystemInfo systemInfo = new SystemInfo();
            HardwareAbstractionLayer hal = systemInfo.getHardware();
            List<HWDiskStore> diskStores = hal.getDiskStores();

            for (HWDiskStore disk : diskStores) {
                if (disk.getSize() <= 0) continue;

                DeviceInfoDto deviceDto = buildDeviceDto(disk);
                devices.add(deviceDto);
            }
            log.info("Detected {} storage devices", devices.size());
        } catch (Exception e) {
            log.error("Error detecting devices: {}", e.getMessage());
            // Return simulated devices for demo/development
            devices.addAll(getSimulatedDevices());
        }
        return devices;
    }

    private DeviceInfoDto buildDeviceDto(HWDiskStore disk) {
        List<DeviceInfoDto.PartitionDto> partitions = new ArrayList<>();

        for (HWPartition partition : disk.getPartitions()) {
            DeviceInfoDto.PartitionDto partitionDto = DeviceInfoDto.PartitionDto.builder()
                    .name(partition.getName())
                    .mountPoint(partition.getMountPoint())
                    .fileSystem(partition.getType())
                    .size(partition.getSize())
                    .sizeFormatted(formatSize(partition.getSize()))
                    .path(partition.getMountPoint().isEmpty() ? partition.getName() : partition.getMountPoint())
                    .type(DeviceType.PARTITION)
                    .build();
            partitions.add(partitionDto);
        }

        DeviceType deviceType = detectDeviceType(disk.getModel(), disk.getName());
        boolean removable = isRemovable(disk.getModel(), disk.getName());

        // On Windows, OSHI disk.getName() looks like "\\.\PHYSICALDRIVE0"
        // We log the path so users can confirm the correct drive index
        log.debug("Device: name={} model={} size={} removable={}",
                disk.getName(), disk.getModel(), disk.getSize(), removable);

        return DeviceInfoDto.builder()
                .id(UUID.randomUUID().toString())
                .name(disk.getName())
                .model(disk.getModel())
                .path(disk.getName())   // This is the raw device path used for wiping
                .size(disk.getSize())
                .sizeFormatted(formatSize(disk.getSize()))
                .type(deviceType)
                .fileSystem(partitions.isEmpty() ? "Unknown" :
                        partitions.get(0).getFileSystem())
                .mountPoint(partitions.isEmpty() ? "" :
                        partitions.get(0).getMountPoint())
                .serialNumber(disk.getSerial())
                .firmwareVersion("N/A")
                .removable(removable)
                .partitions(partitions)
                .build();
    }

    private DeviceType detectDeviceType(String model, String name) {
        String combined = ((model == null ? "" : model) + " " + (name == null ? "" : name)).toLowerCase();

        // NVMe detection
        if (combined.contains("nvme")) {
            return DeviceType.NVME;
        }

        // USB detection — OSHI includes "USB" in model name for removable drives on Windows
        if (combined.contains("usb") || combined.contains("flash") ||
                combined.contains("thumb") || combined.contains("cruzer") ||
                combined.contains("datatraveler") || combined.contains("jetflash") ||
                combined.contains("ultra fit") || combined.contains("ultra flair") ||
                combined.contains("removable")) {
            return DeviceType.USB;
        }

        // SSD detection
        if (combined.contains("ssd") || combined.contains("solid state") ||
                combined.contains("samsung 8") || combined.contains("samsung 9") ||
                combined.contains("crucial") || combined.contains("mx500") ||
                combined.contains("kingston sa")) {
            return DeviceType.SSD;
        }

        // External HDD
        if (combined.contains("external") || combined.contains("portable") ||
                combined.contains("my passport") || combined.contains("elements") ||
                combined.contains("expansion")) {
            return DeviceType.EXTERNAL_HDD;
        }

        // Default to HDD for internal drives
        return DeviceType.HDD;
    }

    private boolean isRemovable(String model, String name) {
        String combined = ((model == null ? "" : model) + " " + (name == null ? "" : name)).toLowerCase();
        return combined.contains("usb") || combined.contains("external") ||
               combined.contains("portable") || combined.contains("removable") ||
               combined.contains("cruzer") || combined.contains("datatraveler") ||
               combined.contains("jetflash") || combined.contains("ultra fit");
    }

    public String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    // Simulated devices for demo/development mode
    private List<DeviceInfoDto> getSimulatedDevices() {
        List<DeviceInfoDto> simulated = new ArrayList<>();

        simulated.add(DeviceInfoDto.builder()
                .id("sim-hdd-001")
                .name("/dev/sda")
                .model("SEAGATE ST2000DM008 HDD")
                .path("/dev/sda")
                .size(2_000_000_000_000L)
                .sizeFormatted("2.0 TB")
                .type(DeviceType.HDD)
                .fileSystem("ext4")
                .mountPoint("/")
                .serialNumber("WR123456789")
                .firmwareVersion("CC43")
                .removable(false)
                .partitions(List.of(
                        DeviceInfoDto.PartitionDto.builder()
                                .name("sda1").mountPoint("/boot").fileSystem("ext4")
                                .size(536_870_912L).sizeFormatted("512.0 MB")
                                .path("/boot").type(DeviceType.PARTITION).build(),
                        DeviceInfoDto.PartitionDto.builder()
                                .name("sda2").mountPoint("/").fileSystem("ext4")
                                .size(1_999_000_000_000L).sizeFormatted("1.9 TB")
                                .path("/").type(DeviceType.PARTITION).build()
                ))
                .build());

        simulated.add(DeviceInfoDto.builder()
                .id("sim-ssd-001")
                .name("/dev/sdb")
                .model("Samsung 870 EVO SSD 500GB")
                .path("/dev/sdb")
                .size(500_107_862_016L)
                .sizeFormatted("500.1 GB")
                .type(DeviceType.SSD)
                .fileSystem("NTFS")
                .mountPoint("/mnt/data")
                .serialNumber("S5GBNX0T123456")
                .firmwareVersion("SVT41B6Q")
                .removable(false)
                .partitions(List.of(
                        DeviceInfoDto.PartitionDto.builder()
                                .name("sdb1").mountPoint("/mnt/data").fileSystem("NTFS")
                                .size(500_000_000_000L).sizeFormatted("500.0 GB")
                                .path("/mnt/data").type(DeviceType.PARTITION).build()
                ))
                .build());

        simulated.add(DeviceInfoDto.builder()
                .id("sim-usb-001")
                .name("/dev/sdc")
                .model("SanDisk Cruzer USB 3.0 64GB")
                .path("/dev/sdc")
                .size(64_023_257_088L)
                .sizeFormatted("64.0 GB")
                .type(DeviceType.USB)
                .fileSystem("FAT32")
                .mountPoint("/media/usb")
                .serialNumber("4C531001234567")
                .firmwareVersion("1.00")
                .removable(true)
                .partitions(List.of(
                        DeviceInfoDto.PartitionDto.builder()
                                .name("sdc1").mountPoint("/media/usb").fileSystem("FAT32")
                                .size(64_000_000_000L).sizeFormatted("64.0 GB")
                                .path("/media/usb").type(DeviceType.PARTITION).build()
                ))
                .build());

        simulated.add(DeviceInfoDto.builder()
                .id("sim-nvme-001")
                .name("/dev/nvme0n1")
                .model("WD Black SN750 NVMe SSD 1TB")
                .path("/dev/nvme0n1")
                .size(1_000_204_886_016L)
                .sizeFormatted("1.0 TB")
                .type(DeviceType.NVME)
                .fileSystem("ext4")
                .mountPoint("/mnt/nvme")
                .serialNumber("21234S801042")
                .firmwareVersion("111110WD")
                .removable(false)
                .partitions(new ArrayList<>())
                .build());

        return simulated;
    }
}
