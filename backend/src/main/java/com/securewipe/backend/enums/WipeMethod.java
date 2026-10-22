package com.securewipe.backend.enums;

public enum WipeMethod {
    ZEROS("Single Pass Zeros", 1,
            "Overwrites entire storage with binary zeros (0x00). Fast and suitable for basic data removal."),
    DOD_3PASS("DoD 5220.22-M (3-Pass)", 3,
            "US Department of Defense standard. Pass 1: 0x00, Pass 2: 0xFF, Pass 3: Random. Meets most government compliance requirements."),
    DOD_7PASS("DoD 5220.22-M ECE (7-Pass)", 7,
            "Enhanced DoD standard for classified data. 7 overwrite passes with alternating patterns and verification. Highest software-based security."),
    RANDOM("Random Data Overwrite", 1,
            "Overwrites with cryptographically secure random data. Effective against data recovery attempts."),
    ATA_SECURE_ERASE("ATA Secure Erase", 1,
            "Issues native ATA Secure Erase command directly to SSD firmware. Most effective method for SSDs as it resets all cells.");

    private final String displayName;
    private final int passes;
    private final String description;

    WipeMethod(String displayName, int passes, String description) {
        this.displayName = displayName;
        this.passes = passes;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public int getPasses() { return passes; }
    public String getDescription() { return description; }
}
