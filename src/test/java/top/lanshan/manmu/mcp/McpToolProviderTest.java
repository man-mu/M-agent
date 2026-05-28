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

}
