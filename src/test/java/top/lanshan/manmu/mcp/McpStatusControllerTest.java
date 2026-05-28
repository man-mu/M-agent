package top.lanshan.manmu.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.config.McpProperties;

import java.util.List;

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

        WebTestClient.bindToController(new McpStatusController(provider)).build()
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
}
