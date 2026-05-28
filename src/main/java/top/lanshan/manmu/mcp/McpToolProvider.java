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
    private volatile List<ServerStatus> serverStatuses = List.of();

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

    public McpStatus getStatus() {
        ToolCallback[] callbacks = getToolCallbacks();
        return new McpStatus(mcpProperties.isEnabled(), serverStatuses, callbacks.length);
    }

    private ToolCallback[] initToolCallbacks() {
        List<McpConfigMergeUtil.NamedTransport> transports = McpConfigMergeUtil.createNamedTransports(
                staticConfig, webClientBuilder, objectMapper);
        List<ServerStatus> statuses = new ArrayList<>();

        if (transports.isEmpty()) {
            logger.info("No enabled MCP servers configured");
            serverStatuses = configuredServerStatuses();
            return new ToolCallback[0];
        }

        List<McpAsyncClient> clients = new ArrayList<>();
        McpSchema.Implementation clientInfo = new McpSchema.Implementation(clientName, clientVersion);

        for (McpConfigMergeUtil.NamedTransport namedTransport : transports) {
            try {
                McpAsyncClient client = McpClient.async(namedTransport.transport())
                    .clientInfo(clientInfo)
                    .build();
                client.initialize().block(Duration.ofMinutes(2));
                clients.add(client);
                statuses.add(ServerStatus.connected(namedTransport.server()));
                logger.info("MCP client initialized: {}", namedTransport.server().getUrl());
            } catch (Exception e) {
                statuses.add(ServerStatus.failed(namedTransport.server(), safeMessage(e)));
                logger.error("Failed to initialize MCP client {}: {}",
                        namedTransport.server().getUrl(), safeMessage(e));
            }
        }

        if (clients.isEmpty()) {
            serverStatuses = withDisabledServers(statuses);
            return new ToolCallback[0];
        }

        AsyncMcpToolCallbackProvider provider = new AsyncMcpToolCallbackProvider(clients);
        ToolCallback[] callbacks = provider.getToolCallbacks();
        serverStatuses = withDisabledServers(statuses);
        logger.info("MCP tools ready: {} tools from {} client(s)", callbacks.length, clients.size());
        return callbacks;
    }

    private List<ServerStatus> configuredServerStatuses() {
        if (staticConfig == null || staticConfig.getMcpServers() == null) {
            return List.of();
        }
        return staticConfig.getMcpServers().stream()
                .map(server -> server.isEnabled()
                        ? ServerStatus.failed(server, "No transport initialized")
                        : ServerStatus.disabled(server))
                .toList();
    }

    private List<ServerStatus> withDisabledServers(List<ServerStatus> activeStatuses) {
        if (staticConfig == null || staticConfig.getMcpServers() == null) {
            return List.copyOf(activeStatuses);
        }
        List<ServerStatus> all = new ArrayList<>(activeStatuses);
        for (McpProperties.McpServerInfo server : staticConfig.getMcpServers()) {
            boolean alreadyTracked = activeStatuses.stream()
                    .anyMatch(status -> status.url().equals(server.getUrl()));
            if (!server.isEnabled() && !alreadyTracked) {
                all.add(ServerStatus.disabled(server));
            }
        }
        return List.copyOf(all);
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    public record McpStatus(boolean enabled, List<ServerStatus> servers, int toolCount) {
    }

    public record ServerStatus(String url, String sseEndpoint, String description,
            boolean configuredEnabled, boolean connected, String error) {

        static ServerStatus connected(McpProperties.McpServerInfo server) {
            return new ServerStatus(server.getUrl(), server.getSseEndpoint(),
                    server.getDescription(), server.isEnabled(), true, "");
        }

        static ServerStatus failed(McpProperties.McpServerInfo server, String error) {
            return new ServerStatus(server.getUrl(), server.getSseEndpoint(),
                    server.getDescription(), server.isEnabled(), false, error);
        }

        static ServerStatus disabled(McpProperties.McpServerInfo server) {
            return new ServerStatus(server.getUrl(), server.getSseEndpoint(),
                    server.getDescription(), server.isEnabled(), false, "");
        }
    }

}
