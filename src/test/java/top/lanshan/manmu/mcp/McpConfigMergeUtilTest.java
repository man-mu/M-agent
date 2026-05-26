package top.lanshan.manmu.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.lanshan.manmu.config.McpProperties;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpConfigMergeUtilTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mergesStaticOnlyWhenNoRuntime() {
        McpProperties.McpServerConfig staticConfig = new McpProperties.McpServerConfig(List.of(
                server("https://example.com/mcp", "/sse", "Test", true)));

        McpProperties.McpServerConfig result = McpConfigMergeUtil.merge(
                staticConfig, null, objectMapper);

        assertThat(result.getMcpServers()).hasSize(1);
        assertThat(result.getMcpServers().get(0).getUrl()).isEqualTo("https://example.com/mcp");
    }

    @Test
    void runtimeOverridesStaticByUrl() {
        McpProperties.McpServerConfig staticConfig = new McpProperties.McpServerConfig(List.of(
                server("https://example.com/mcp", "/sse", "Static", true)));

        Map<String, Object> runtime = Map.of("mcp-servers", List.of(
                Map.of("url", "https://example.com/mcp", "enabled", false)));

        McpProperties.McpServerConfig result = McpConfigMergeUtil.merge(
                staticConfig, runtime, objectMapper);

        assertThat(result.getMcpServers()).hasSize(1);
        assertThat(result.getMcpServers().get(0).isEnabled()).isFalse();
    }

    @Test
    void runtimeAppendsNewServers() {
        McpProperties.McpServerConfig staticConfig = new McpProperties.McpServerConfig(List.of(
                server("https://a.com", "/sse", "A", true)));

        Map<String, Object> runtime = Map.of("mcp-servers", List.of(
                Map.of("url", "https://b.com", "sse-endpoint", "/sse", "description", "B",
                        "enabled", true)));

        McpProperties.McpServerConfig result = McpConfigMergeUtil.merge(
                staticConfig, runtime, objectMapper);

        assertThat(result.getMcpServers()).hasSize(2);
    }

    @Test
    void returnsEmptyForNullConfig() {
        McpProperties.McpServerConfig result = McpConfigMergeUtil.merge(
                null, null, objectMapper);

        assertThat(result.getMcpServers()).isEmpty();
    }

    @Test
    void handlesMalformedRuntimeSettings() {
        McpProperties.McpServerConfig staticConfig = new McpProperties.McpServerConfig(List.of(
                server("https://example.com/mcp", "/sse", "Test", true)));

        Map<String, Object> badRuntime = Map.of("mcp-servers", "not-a-list");

        McpProperties.McpServerConfig result = McpConfigMergeUtil.merge(
                staticConfig, badRuntime, objectMapper);

        assertThat(result.getMcpServers()).hasSize(1);
    }

    private static McpProperties.McpServerInfo server(String url, String sseEndpoint,
            String description, boolean enabled) {
        McpProperties.McpServerInfo info = new McpProperties.McpServerInfo();
        info.setUrl(url);
        info.setSseEndpoint(sseEndpoint);
        info.setDescription(description);
        info.setEnabled(enabled);
        return info;
    }
}
