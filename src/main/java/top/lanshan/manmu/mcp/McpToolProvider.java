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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.lanshan.manmu.config.McpProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class McpToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(McpToolProvider.class);

    private final McpProperties mcpProperties;
    private final McpProperties.McpServerConfig staticConfig;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final String clientName;
    private final String clientVersion;
    private final Duration initTimeout = Duration.ofMinutes(2);

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
            cachedCallbacks = Mono.fromCallable(this::initToolCallbacks)
                    .subscribeOn(Schedulers.boundedElastic())
                    .block(initTimeout);
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

        Set<String> allowedTools = allowedTools(transports);
        AsyncMcpToolCallbackProvider provider = allowedTools.isEmpty()
                ? new AsyncMcpToolCallbackProvider(clients)
                : new AsyncMcpToolCallbackProvider(
                    (connectionInfo, tool) -> allowedTools.contains(tool.name()), clients);
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

    Set<String> allowedTools(List<McpConfigMergeUtil.NamedTransport> transports) {
        Set<String> tools = new LinkedHashSet<>();
        for (McpConfigMergeUtil.NamedTransport transport : transports) {
            List<String> allowed = transport.server().getAllowedTools();
            if (allowed != null) {
                allowed.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::strip)
                        .forEach(tools::add);
            }
        }
        return tools;
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
            boolean configuredEnabled, boolean connected, String error,
            List<String> allowedTools, String keyEnvName, Boolean keyConfigured,
            List<String> requiredEnvVars) {

        static ServerStatus connected(McpProperties.McpServerInfo server) {
            return new ServerStatus(server.getUrl(), server.getSseEndpoint(),
                    server.getDescription(), server.isEnabled(), true, "",
                    allowedTools(server), keyEnvName(server), keyConfigured(server),
                    requiredEnvVars(server));
        }

        static ServerStatus failed(McpProperties.McpServerInfo server, String error) {
            return new ServerStatus(server.getUrl(), server.getSseEndpoint(),
                    server.getDescription(), server.isEnabled(), false, error,
                    allowedTools(server), keyEnvName(server), keyConfigured(server),
                    requiredEnvVars(server));
        }

        static ServerStatus disabled(McpProperties.McpServerInfo server) {
            return new ServerStatus(server.getUrl(), server.getSseEndpoint(),
                    server.getDescription(), server.isEnabled(), false, "",
                    allowedTools(server), keyEnvName(server), keyConfigured(server),
                    requiredEnvVars(server));
        }

        private static List<String> allowedTools(McpProperties.McpServerInfo server) {
            if (server.getAllowedTools() == null) {
                return List.of();
            }
            return server.getAllowedTools().stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::strip)
                    .distinct()
                    .toList();
        }

        private static List<String> requiredEnvVars(McpProperties.McpServerInfo server) {
            List<String> envNames = new ArrayList<>();
            envNames.addAll(McpConfigMergeUtil.placeholderNames(server.getUrl()));
            envNames.addAll(McpConfigMergeUtil.placeholderNames(server.getSseEndpoint()));
            if (allowedTools(server).contains("weather_now") && !envNames.contains("QWEATHER_API_KEY")) {
                envNames.add("QWEATHER_API_KEY");
            }
            return envNames.stream().distinct().toList();
        }

        private static String keyEnvName(McpProperties.McpServerInfo server) {
            List<String> names = requiredEnvVars(server);
            return names.isEmpty() ? null : names.get(0);
        }

        private static Boolean keyConfigured(McpProperties.McpServerInfo server) {
            List<String> names = requiredEnvVars(server);
            if (names.isEmpty()) {
                return null;
            }
            return names.stream().allMatch(McpConfigMergeUtil::hasConfiguredValue);
        }
    }

}
