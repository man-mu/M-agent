package top.lanshan.manmu.skill.market;

import java.time.Instant;

public class SkillCatalogEntry {

    private String name;
    private String version = "1.0.0";
    private SkillPackageType packageType = SkillPackageType.PROMPT;
    private SkillPackageStatus status = SkillPackageStatus.INSTALLED;
    private String source = "local";
    private Instant installedAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public SkillCatalogEntry() {
    }

    public SkillCatalogEntry(String name, String version, SkillPackageType packageType,
            SkillPackageStatus status, String source, Instant installedAt, Instant updatedAt) {
        this.name = name;
        this.version = version;
        this.packageType = packageType;
        this.status = status;
        this.source = source;
        this.installedAt = installedAt;
        this.updatedAt = updatedAt;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public SkillPackageType getPackageType() { return packageType; }
    public void setPackageType(SkillPackageType packageType) { this.packageType = packageType; }

    public SkillPackageStatus getStatus() { return status; }
    public void setStatus(SkillPackageStatus status) { this.status = status; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Instant getInstalledAt() { return installedAt; }
    public void setInstalledAt(Instant installedAt) { this.installedAt = installedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
