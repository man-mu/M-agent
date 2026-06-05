package top.lanshan.manmu.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.config.McpProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolProviderTest {

    @Test
    void returnsEmptyWhenMcpDisabled() {
        McpProperties props = new McpProperties();
        props.setEnabled(false);

        McpToolProvider provider = new McpToolProvider(props,
                new McpProperties.McpServerConfig(),
                WebClient.builder(),
                new ObjectMapper(),
                "test", "1.0");

        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertThat(callbacks).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoEnabledServers() {
        McpProperties props = new McpProperties();
        props.setEnabled(true);

        McpProperties.McpServerInfo info = new McpProperties.McpServerInfo();
        info.setUrl("https://example.com");
        info.setEnabled(false);

        McpToolProvider provider = new McpToolProvider(props,
                new McpProperties.McpServerConfig(List.of(info)),
                WebClient.builder(),
                new ObjectMapper(),
                "test", "1.0");

        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertThat(callbacks).isEmpty();

        McpToolProvider.McpStatus status = provider.getStatus();
        assertThat(status.enabled()).isTrue();
        assertThat(status.toolCount()).isZero();
        assertThat(status.servers()).hasSize(1);
        assertThat(status.servers().get(0).configuredEnabled()).isFalse();
        assertThat(status.servers().get(0).connected()).isFalse();
    }

    @Test
    void cachesResultAfterFirstCall() {
        McpProperties props = new McpProperties();
        props.setEnabled(true);

        McpToolProvider provider = new McpToolProvider(props,
                new McpProperties.McpServerConfig(),
                WebClient.builder(),
                new ObjectMapper(),
                "test", "1.0");

        ToolCallback[] first = provider.getToolCallbacks();
        ToolCallback[] second = provider.getToolCallbacks();
        assertThat(first).isSameAs(second);
    }

    @Test
    void collectsAllowedToolsFromEnabledServers() {
        McpProperties props = new McpProperties();
        props.setEnabled(true);

        McpProperties.McpServerInfo info = new McpProperties.McpServerInfo();
        info.setUrl("https://mcp.amap.com");
        info.setAllowedTools(List.of("maps_weather", " ", "maps_geo", "maps_weather"));

        McpToolProvider provider = new McpToolProvider(props,
                new McpProperties.McpServerConfig(List.of(info)),
                WebClient.builder(),
                new ObjectMapper(),
                "test", "1.0");

        assertThat(provider.allowedTools(List.of(new McpConfigMergeUtil.NamedTransport(info, null))))
                .containsExactly("maps_weather", "maps_geo");
    }

    @Test
    void exposesNonSensitiveServerMetadataInStatus() {
        McpProperties props = new McpProperties();
        props.setEnabled(true);

        McpProperties.McpServerInfo info = new McpProperties.McpServerInfo();
        info.setUrl("http://127.0.0.1:18090");
        info.setDescription("本地和风天气 MCP");
        info.setEnabled(false);
        info.setAllowedTools(List.of("weather_now", "weather_now", " "));

        McpToolProvider provider = new McpToolProvider(props,
                new McpProperties.McpServerConfig(List.of(info)),
                WebClient.builder(),
                new ObjectMapper(),
                "test", "1.0");

        McpToolProvider.ServerStatus status = provider.getStatus().servers().get(0);
        assertThat(status.allowedTools()).containsExactly("weather_now");
        assertThat(status.requiredEnvVars()).containsExactly("QWEATHER_API_KEY");
        assertThat(status.keyEnvName()).isEqualTo("QWEATHER_API_KEY");
        assertThat(status.toString()).doesNotContain("secret");
    }

    @Test
    void connectionTestErrorsAreReadableAndSanitized() {
        McpProperties.McpServerInfo info = new McpProperties.McpServerInfo();
        info.setId("local-test");
        info.setUrl("http://localhost:18090");
        info.setSseEndpoint("/sse?token=secret-value");
        info.setAllowedTools(List.of("weather_now"));

        McpToolProvider.McpConnectionTestResult timeout =
                McpToolProvider.McpConnectionTestResult.failed(info,
                        "java.util.concurrent.TimeoutException: Did not observe any item or terminal signal",
                        20_000);
        McpToolProvider.McpConnectionTestResult refused =
                McpToolProvider.McpConnectionTestResult.failed(info,
                        "java.net.ConnectException: Connection refused: token=secret-value",
                        5);

        assertThat(timeout.error()).isEqualTo("Connection timed out while testing MCP server");
        assertThat(refused.error()).isEqualTo("MCP server is not reachable");
        assertThat(timeout.toString()).doesNotContain("secret-value");
        assertThat(refused.toString()).doesNotContain("secret-value");
    }

}
