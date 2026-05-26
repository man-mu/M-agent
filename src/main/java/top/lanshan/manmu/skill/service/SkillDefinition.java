package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonProperty("created_at")
    private Instant createdAt = Instant.now();

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

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
