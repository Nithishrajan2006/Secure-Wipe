package com.securewipe.backend.enums;

public enum DeviceType {
    HDD("Hard Disk Drive"),
    SSD("Solid State Drive"),
    USB("USB Flash Drive"),
    EXTERNAL_HDD("External Hard Drive"),
    NVME("NVMe Drive"),
    PARTITION("Partition"),
    FILE("File"),
    FOLDER("Folder");

    private final String displayName;

    DeviceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
