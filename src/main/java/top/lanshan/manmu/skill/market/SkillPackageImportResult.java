package top.lanshan.manmu.skill.market;

import top.lanshan.manmu.skill.service.SkillStorageLocation;

public class SkillPackageImportResult {

    private String name;
    private String version;
    private SkillPackageType packageType;
    private SkillStorageLocation storageLocation;
    private boolean enabled;
    private String message;

    public SkillPackageImportResult() {
    }

    public SkillPackageImportResult(String name, String version, SkillPackageType packageType,
            SkillStorageLocation storageLocation, boolean enabled, String message) {
        this.name = name;
        this.version = version;
        this.packageType = packageType;
        this.storageLocation = storageLocation;
        this.enabled = enabled;
        this.message = message;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public SkillPackageType getPackageType() { return packageType; }
    public void setPackageType(SkillPackageType packageType) { this.packageType = packageType; }

    public SkillStorageLocation getStorageLocation() { return storageLocation; }
    public void setStorageLocation(SkillStorageLocation storageLocation) { this.storageLocation = storageLocation; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
