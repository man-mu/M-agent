package top.lanshan.manmu.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "mvp.mcp")
public class McpProperties {

    private boolean enabled = true;

    private String configLocation = "classpath:mcp-config.json";
    private String localConfigPath = ".local/mcp-servers.json";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getConfigLocation() { return configLocation; }
    public void setConfigLocation(String configLocation) { this.configLocation = configLocation; }
    public String getLocalConfigPath() { return localConfigPath; }
    public void setLocalConfigPath(String localConfigPath) { this.localConfigPath = localConfigPath; }

    public static class McpServerConfig {
        @JsonProperty("mcp-servers")
        private List<McpServerInfo> mcpServers = new ArrayList<>();

        public List<McpServerInfo> getMcpServers() { return mcpServers; }
        public void setMcpServers(List<McpServerInfo> mcpServers) { this.mcpServers = mcpServers; }

        public McpServerConfig() {}

        public McpServerConfig(List<McpServerInfo> mcpServers) {
            this.mcpServers = mcpServers;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class McpServerInfo {
        private String id;
        private String url;
        @JsonProperty("sse-endpoint")
        @JsonAlias("sseEndpoint")
        private String sseEndpoint = "/sse";
        private String description;
        private boolean enabled = true;
        @JsonProperty("allowed-tools")
        @JsonAlias("allowedTools")
        private List<String> allowedTools = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getSseEndpoint() { return sseEndpoint; }
        public void setSseEndpoint(String sseEndpoint) { this.sseEndpoint = sseEndpoint; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getAllowedTools() { return allowedTools; }
        public void setAllowedTools(List<String> allowedTools) { this.allowedTools = allowedTools; }
    }
}
