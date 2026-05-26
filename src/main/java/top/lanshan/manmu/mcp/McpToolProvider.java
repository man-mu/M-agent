package top.lanshan.manmu.mcp;

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

public class McpToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(McpToolProvider.class);

    private final McpProperties mcpProperties;
    private final McpProperties.McpServerConfig staticConfig;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final String clientName;
    private final String clientVersion;

    private volatile ToolCallback[] cachedCallbacks;
    private volatile boolean initialized;

    public McpToolProvider(McpProperties mcpProperties,
            McpProperties.McpServerConfig staticConfig,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            String clientName,
            String clientVersion) {
        this.mcpProperties = mcpProperties;
        this.staticConfig = staticConfig;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.clientName = clientName;
        this.clientVersion = clientVersion;
    }

    public ToolCallback[] getToolCallbacks() {
        if (!mcpProperties.isEnabled()) {
            return new ToolCallback[0];
        }
        if (initialized) {
            return cachedCallbacks;
        }
        synchronized (this) {
            if (initialized) {
                return cachedCallbacks;
            }
            cachedCallbacks = initToolCallbacks();
            initialized = true;
        }
        return cachedCallbacks;
    }

    private ToolCallback[] initToolCallbacks() {
        List<WebFluxSseClientTransport> transports = McpConfigMergeUtil.createTransports(
                staticConfig, webClientBuilder, objectMapper);

        if (transports.isEmpty()) {
            logger.info("No enabled MCP servers configured");
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
                logger.info("MCP client initialized: {}", clientInfo.name());
            } catch (Exception e) {
                logger.error("Failed to initialize MCP client: {}", e.getMessage());
            }
        }

        if (clients.isEmpty()) {
            return new ToolCallback[0];
        }

        AsyncMcpToolCallbackProvider provider = new AsyncMcpToolCallbackProvider(clients);
        ToolCallback[] callbacks = provider.getToolCallbacks();
        logger.info("MCP tools ready: {} tools from {} client(s)", callbacks.length, clients.size());
        return callbacks;
    }

}
