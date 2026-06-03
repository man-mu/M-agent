package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import top.lanshan.manmu.skill.market.SkillPackageType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillDefinition {

    private String name;
    private String description;
    private String version = "1.0.0";
    private boolean enabled = true;
    private Map<String, Object> parameters;
    private List<String> dependencies = new ArrayList<>();
    private String displayName;
    private String category;
    private String author;
    private String homepage;
    private List<String> tags = new ArrayList<>();
    private SkillPackageType packageType = SkillPackageType.PROMPT;
    private String source;
    private SkillStorageLocation storageLocation;

    @JsonProperty("created_at")
    private Instant createdAt = Instant.now();

    @JsonProperty("installed_at")
    private Instant installedAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public SkillDefinition() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getHomepage() { return homepage; }
    public void setHomepage(String homepage) { this.homepage = homepage; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public SkillPackageType getPackageType() { return packageType; }
    public void setPackageType(SkillPackageType packageType) { this.packageType = packageType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public SkillStorageLocation getStorageLocation() { return storageLocation; }
    public void setStorageLocation(SkillStorageLocation storageLocation) { this.storageLocation = storageLocation; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getInstalledAt() { return installedAt; }
    public void setInstalledAt(Instant installedAt) { this.installedAt = installedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Returns the JSON Schema string for this skill's input parameters.
     * Returns {@code "{}"} if parameters is null, so the ToolDefinition inputSchema
     * is never null (required by Spring AI).
     */
    public String getInputSchemaJson() {
        if (parameters == null) {
            return "{}";
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(parameters);
        } catch (Exception e) {
            return "{}";
        }
    }
}
