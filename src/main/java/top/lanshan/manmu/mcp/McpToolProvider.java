package top.lanshan.manmu.mcp;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.config.McpProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class McpToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(McpToolProvider.class);

    private final McpProperties mcpProperties;
    private final McpProperties.McpServerConfig staticConfig;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final String clientName;
    private final String clientVersion;
    private final Function<OverAllState, Map<String, Object>> runtimeSettingsExtractor;

    public McpToolProvider(McpProperties mcpProperties,
            McpProperties.McpServerConfig staticConfig,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            String clientName,
            String clientVersion,
            Function<OverAllState, Map<String, Object>> runtimeSettingsExtractor) {
        this.mcpProperties = mcpProperties;
        this.staticConfig = staticConfig;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.clientName = clientName;
        this.clientVersion = clientVersion;
        this.runtimeSettingsExtractor = runtimeSettingsExtractor;
    }

    public ToolCallback[] getToolCallbacks(OverAllState state) {
        if (!mcpProperties.isEnabled()) {
            return new ToolCallback[0];
        }

        Map<String, Object> runtimeSettings = runtimeSettingsExtractor != null
                ? runtimeSettingsExtractor.apply(state) : null;

        McpProperties.McpServerConfig mergedConfig = McpConfigMergeUtil.merge(
                staticConfig, runtimeSettings, objectMapper);

        List<WebFluxSseClientTransport> transports = McpConfigMergeUtil.createTransports(
                mergedConfig, webClientBuilder, objectMapper);

        if (transports.isEmpty()) {
            return new ToolCallback[0];
        }

        List<McpAsyncClient> clients = new ArrayList<>();
        McpSchema.Implementation clientInfo = new McpSchema.Implementation(clientName, clientVersion);

        for (WebFluxSseClientTransport transport : transports) {
            try {
                McpAsyncClient client = McpClient.async(transport)
                    .clientInfo(clientInfo)
                    .build();
                client.initialize().block(Duration.ofMinutes(2));
                clients.add(client);
                logger.info("MCP client initialized: {}", clientInfo);
            } catch (Exception e) {
                logger.error("Failed to initialize MCP client: {}", e.getMessage());
            }
        }

        if (clients.isEmpty()) {
            return new ToolCallback[0];
        }

        AsyncMcpToolCallbackProvider provider = new AsyncMcpToolCallbackProvider(clients);
        ToolCallback[] callbacks = provider.getToolCallbacks();
        logger.info("MCP tools available: {} tools from {} clients", callbacks.length, clients.size());
        return callbacks;
    }
}
