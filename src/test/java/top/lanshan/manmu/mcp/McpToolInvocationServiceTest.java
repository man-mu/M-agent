package top.lanshan.manmu.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.config.McpProperties;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolInvocationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void invokesConnectedToolAndSanitizesInput() {
        RecordingTool tool = new RecordingTool("weather_now", "上海当前多云，温度 26°C。");
        McpToolInvocationService service = serviceWith(tool);

        McpToolInvocationService.McpToolInvocationResult result =
                service.invoke("weather_now", Map.of("location", "上海", "apiKey", "secret-value"));

        assertThat(result.error()).isBlank();
        assertThat(result.output()).contains("上海当前多云");
        assertThat(result.input()).containsEntry("apiKey", "***");
        assertThat(tool.lastInput).contains("\"location\":\"上海\"");
        assertThat(tool.lastInput).contains("secret-value");
        assertThat(result.toString()).doesNotContain("secret-value");
    }

    @Test
    void returnsReadableErrorWhenToolIsMissing() {
        McpToolInvocationService service = serviceWith();

        McpToolInvocationService.McpToolInvocationResult result =
                service.invoke("missing_tool", Map.of());

        assertThat(result.output()).isBlank();
        assertThat(result.error()).contains("not connected");
    }

    @Test
    void matchesSpringAiPrefixedMcpToolName() {
        RecordingTool tool = new RecordingTool("deepresearch_mvp_weather_now", "ok");
        McpToolInvocationService service = serviceWith(tool);

        McpToolInvocationService.McpToolInvocationResult result =
                service.invoke("weather_now", Map.of("location", "Shanghai"));

        assertThat(result.error()).isBlank();
        assertThat(result.output()).isEqualTo("ok");
        assertThat(tool.lastInput).contains("\"location\":\"Shanghai\"");
    }

    @Test
    void truncatesLargeOutputAndSanitizesErrors() {
        RecordingTool longOutput = new RecordingTool("large_tool",
                "x".repeat(McpToolInvocationService.MAX_OUTPUT_CHARS + 50));
        RecordingTool failing = new RecordingTool("failing_tool", null);
        failing.error = new IllegalStateException("failed with token=secret-value");
        McpToolInvocationService service = serviceWith(longOutput, failing);

        McpToolInvocationService.McpToolInvocationResult large =
                service.invoke("large_tool", Map.of());
        McpToolInvocationService.McpToolInvocationResult failed =
                service.invoke("failing_tool", Map.of());

        assertThat(large.output()).hasSize(McpToolInvocationService.MAX_OUTPUT_CHARS + "\n...[truncated]".length());
        assertThat(large.output()).endsWith("...[truncated]");
        assertThat(failed.error()).contains("token=***");
        assertThat(failed.toString()).doesNotContain("secret-value");
    }

    private McpToolInvocationService serviceWith(ToolCallback... callbacks) {
        return new McpToolInvocationService(new StaticMcpToolProvider(callbacks), objectMapper);
    }

    private static class StaticMcpToolProvider extends McpToolProvider {
        private final ToolCallback[] callbacks;

        StaticMcpToolProvider(ToolCallback... callbacks) {
            super(new McpProperties(), new McpProperties.McpServerConfig(),
                    WebClient.builder(), new ObjectMapper(), "test", "0.0.0");
            this.callbacks = callbacks;
        }

        @Override
        public ToolCallback[] getToolCallbacks() {
            return callbacks;
        }
    }

    private static class RecordingTool implements ToolCallback {
        private final String name;
        private final String output;
        private RuntimeException error;
        private String lastInput;

        RecordingTool(String name, String output) {
            this.name = name;
            this.output = output;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return DefaultToolDefinition.builder()
                    .name(name)
                    .description("test tool")
                    .inputSchema("{}")
                    .build();
        }

        @Override
        public String call(String toolInput) {
            this.lastInput = toolInput;
            if (error != null) {
                throw error;
            }
            return output;
        }
    }
}
