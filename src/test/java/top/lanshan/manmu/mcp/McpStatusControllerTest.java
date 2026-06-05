package top.lanshan.manmu.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.config.McpProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class McpStatusControllerTest {

    @Test
    void returnsMcpStatusWithoutConnectingDisabledServers() {
        McpProperties props = new McpProperties();
        props.setEnabled(true);

        McpProperties.McpServerInfo info = new McpProperties.McpServerInfo();
        info.setUrl("https://example.com/mcp");
        info.setSseEndpoint("/sse");
        info.setDescription("Example");
        info.setEnabled(false);

        McpToolProvider provider = new McpToolProvider(props,
                new McpProperties.McpServerConfig(List.of(info)),
                WebClient.builder(),
                new ObjectMapper(),
                "test", "1.0");

        McpServerConfigService configService = new McpServerConfigService(
                new McpProperties.McpServerConfig(List.of(info)),
                tempConfigPath(),
                new ObjectMapper());

        WebTestClient.bindToController(new McpStatusController(provider, configService,
                        new McpToolInvocationService(provider, new ObjectMapper()))).build()
                .get()
                .uri("/api/mcp/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(true)
                .jsonPath("$.toolCount").isEqualTo(0)
                .jsonPath("$.servers[0].url").isEqualTo("https://example.com/mcp")
                .jsonPath("$.servers[0].configuredEnabled").isEqualTo(false)
                .jsonPath("$.servers[0].connected").isEqualTo(false);
    }

    @Test
    void invokesMcpToolThroughController() {
        McpProperties props = new McpProperties();
        props.setEnabled(true);
        RecordingTool tool = new RecordingTool();
        McpToolProvider provider = new StaticMcpToolProvider(props, tool);
        McpServerConfigService configService = new McpServerConfigService(
                new McpProperties.McpServerConfig(),
                tempConfigPath(),
                new ObjectMapper());

        WebTestClient.bindToController(new McpStatusController(provider, configService,
                        new McpToolInvocationService(provider, new ObjectMapper()))).build()
                .post()
                .uri("/api/mcp/tools/weather_now/invoke")
                .bodyValue(Map.of("location", "上海"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.toolName").isEqualTo("weather_now")
                .jsonPath("$.output").isEqualTo("上海当前多云")
                .jsonPath("$.error").isEqualTo("");
    }

    private Path tempConfigPath() {
        try {
            return Files.createTempDirectory("mcp-status-controller")
                    .resolve("mcp-servers.json");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static class StaticMcpToolProvider extends McpToolProvider {
        private final ToolCallback[] callbacks;

        StaticMcpToolProvider(McpProperties props, ToolCallback... callbacks) {
            super(props, new McpProperties.McpServerConfig(),
                    WebClient.builder(), new ObjectMapper(), "test", "1.0");
            this.callbacks = callbacks;
        }

        @Override
        public ToolCallback[] getToolCallbacks() {
            return callbacks;
        }
    }

    private static class RecordingTool implements ToolCallback {
        @Override
        public ToolDefinition getToolDefinition() {
            return DefaultToolDefinition.builder()
                    .name("weather_now")
                    .description("Weather now")
                    .inputSchema("{}")
                    .build();
        }

        @Override
        public String call(String toolInput) {
            return "上海当前多云";
        }
    }
}
