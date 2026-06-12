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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class McpToolProvider {

	private static final Logger logger = LoggerFactory.getLogger(McpToolProvider.class);

	private final McpProperties mcpProperties;
	private final Supplier<McpProperties.McpServerConfig> configSupplier;
	private final WebClient.Builder webClientBuilder;
	private final ObjectMapper objectMapper;
	private final String clientName;
	private final String clientVersion;
	private final Duration initTimeout = Duration.ofMinutes(2);
	private final Duration connectionTestTimeout = Duration.ofSeconds(30);

	private volatile ToolCallback[] cachedCallbacks;
	private volatile List<McpAsyncClient> cachedClients = List.of();
	private volatile boolean initialized;
	private volatile List<ServerStatus> serverStatuses = List.of();

	public McpToolProvider(McpProperties mcpProperties,
			McpProperties.McpServerConfig staticConfig,
			WebClient.Builder webClientBuilder,
			ObjectMapper objectMapper,
			String clientName,
			String clientVersion) {
		this(mcpProperties, () -> staticConfig, webClientBuilder, objectMapper, clientName, clientVersion);
	}

	public McpToolProvider(McpProperties mcpProperties,
			Supplier<McpProperties.McpServerConfig> configSupplier,
			WebClient.Builder webClientBuilder,
			ObjectMapper objectMapper,
			String clientName,
			String clientVersion) {
		this.mcpProperties = mcpProperties;
		this.configSupplier = configSupplier;
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

	public McpStatus reload() {
		clearCache();
		return getStatus();
	}

	public synchronized void clearCache() {
		closeClients(cachedClients);
		cachedClients = List.of();
		cachedCallbacks = null;
		initialized = false;
		serverStatuses = configuredServerStatuses(currentConfig());
	}

	public McpConnectionTestResult testConnection(McpProperties.McpServerInfo server) {
		return testConnection(server, connectionTestTimeout);
	}

	public McpConnectionTestResult testConnection(McpProperties.McpServerInfo server, Duration timeout) {
		long started = System.nanoTime();
		McpAsyncClient client = null;
		Duration effectiveTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
				? connectionTestTimeout
				: timeout;
		try {
			McpProperties.McpServerConfig config = new McpProperties.McpServerConfig(List.of(server));
			List<McpConfigMergeUtil.NamedTransport> transports = McpConfigMergeUtil.createNamedTransports(
					config, webClientBuilder, objectMapper);
			if (transports.isEmpty()) {
				return McpConnectionTestResult.failed(server, "No transport initialized",
						durationMs(started));
			}
			McpConfigMergeUtil.NamedTransport transport = transports.get(0);
			client = McpClient.async(transport.transport())
					.clientInfo(new McpSchema.Implementation(clientName, clientVersion))
					.build();
			client.initialize().block(effectiveTimeout);
			McpSchema.ListToolsResult tools = client.listTools().block(effectiveTimeout);
			List<String> toolNames = tools == null || tools.tools() == null
					? List.of()
					: tools.tools().stream().map(McpSchema.Tool::name).toList();
			return McpConnectionTestResult.connected(server, toolNames, durationMs(started));
		} catch (Exception e) {
			return McpConnectionTestResult.failed(server, safeMessage(e), durationMs(started));
		} finally {
			if (client != null) {
				closeClients(List.of(client));
			}
		}
	}

	private ToolCallback[] initToolCallbacks() {
		McpProperties.McpServerConfig currentConfig = currentConfig();
		List<McpConfigMergeUtil.NamedTransport> transports = McpConfigMergeUtil.createNamedTransports(
				currentConfig, webClientBuilder, objectMapper);
		List<ServerStatus> statuses = new ArrayList<>();

		if (transports.isEmpty()) {
			logger.info("No enabled MCP servers configured");
			serverStatuses = configuredServerStatuses(currentConfig);
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
			cachedClients = List.of();
			serverStatuses = withDisabledServers(statuses, currentConfig);
			return new ToolCallback[0];
		}

		Set<String> allowedTools = allowedTools(transports);
		AsyncMcpToolCallbackProvider provider = allowedTools.isEmpty()
				? new AsyncMcpToolCallbackProvider(clients)
				: new AsyncMcpToolCallbackProvider(
					(connectionInfo, tool) -> allowedTools.contains(tool.name()), clients);
		ToolCallback[] callbacks = provider.getToolCallbacks();
		closeClients(cachedClients);
		cachedClients = List.copyOf(clients);
		serverStatuses = withDisabledServers(statuses, currentConfig);
		logger.info("MCP tools ready: {} tools from {} client(s)", callbacks.length, clients.size());
		return callbacks;
	}

	private McpProperties.McpServerConfig currentConfig() {
		McpProperties.McpServerConfig config = configSupplier.get();
		return config == null ? new McpProperties.McpServerConfig() : config;
	}

	private List<ServerStatus> configuredServerStatuses(McpProperties.McpServerConfig config) {
		if (config == null || config.getMcpServers() == null) {
			return List.of();
		}
		return config.getMcpServers().stream()
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

	private List<ServerStatus> withDisabledServers(List<ServerStatus> activeStatuses,
			McpProperties.McpServerConfig config) {
		if (config == null || config.getMcpServers() == null) {
			return List.copyOf(activeStatuses);
		}
		List<ServerStatus> all = new ArrayList<>(activeStatuses);
		for (McpProperties.McpServerInfo server : config.getMcpServers()) {
			boolean alreadyTracked = activeStatuses.stream()
					.anyMatch(status -> Objects.equals(status.id(), server.getId()));
			if (!server.isEnabled() && !alreadyTracked) {
				all.add(ServerStatus.disabled(server));
			}
		}
		return List.copyOf(all);
	}

	private static String safeMessage(Exception e) {
		String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
		return sanitizeSensitiveMessage(message);
	}

	private static String sanitizeSensitiveMessage(String message) {
		if (message == null) {
			return "";
		}
		String sanitized = message.replaceAll("(?i)(key|token|api[_-]?key|access[_-]?key)=([^&\\s]+)", "$1=***");
		String lower = sanitized.toLowerCase();
		if (lower.contains("timeoutexception")
				|| lower.contains("did not observe any item or terminal signal")
				|| lower.contains("timed out")) {
			return "Connection timed out while testing MCP server";
		}
		if (lower.contains("connection refused") || lower.contains("connectexception")) {
			return "MCP server is not reachable";
		}
		return sanitized;
	}

	private static String sanitizeConfigValue(String value) {
		if (value == null) {
			return null;
		}
		return value.replaceAll("(?i)([?&](?:key|token|api[_-]?key|access[_-]?key)=)(?!\\$\\{)[^&\\s]+", "$1***");
	}

	private static long durationMs(long startedNanos) {
		return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
	}

	private void closeClients(List<McpAsyncClient> clients) {
		for (McpAsyncClient client : clients) {
			try {
				client.closeGracefully().block(Duration.ofSeconds(5));
			} catch (Exception e) {
				try {
					client.close();
				} catch (Exception ignored) {
					// best effort cleanup
				}
			}
		}
	}

	public record McpStatus(boolean enabled, List<ServerStatus> servers, int toolCount) {
	}

	public record ServerStatus(String id, String url, String sseEndpoint, String description,
			boolean configuredEnabled, boolean connected, String error,
			List<String> allowedTools, String keyEnvName, Boolean keyConfigured,
			List<String> requiredEnvVars, String source, boolean editable, boolean localOverride,
			boolean hasHeaders, boolean hasApiKey, List<String> headerNames) {

		static ServerStatus connected(McpProperties.McpServerInfo server) {
			return new ServerStatus(server.getId(), sanitizeConfigValue(server.getUrl()),
					sanitizeConfigValue(server.getSseEndpoint()),
					server.getDescription(), server.isEnabled(), true, "",
					allowedTools(server), keyEnvName(server), keyConfigured(server),
					requiredEnvVars(server), serverSource(server), true, serverLocalOverride(server),
					effectiveHeaders(server) != null && !effectiveHeaders(server).isEmpty(),
					server.getApiKey() != null && !server.getApiKey().isBlank(),
					headerNames(server));
		}

		static ServerStatus failed(McpProperties.McpServerInfo server, String error) {
			return new ServerStatus(server.getId(), sanitizeConfigValue(server.getUrl()),
					sanitizeConfigValue(server.getSseEndpoint()),
					server.getDescription(), server.isEnabled(), false, sanitizeSensitiveMessage(error),
					allowedTools(server), keyEnvName(server), keyConfigured(server),
					requiredEnvVars(server), serverSource(server), true, serverLocalOverride(server),
					effectiveHeaders(server) != null && !effectiveHeaders(server).isEmpty(),
					server.getApiKey() != null && !server.getApiKey().isBlank(),
					headerNames(server));
		}

		static ServerStatus disabled(McpProperties.McpServerInfo server) {
			return new ServerStatus(server.getId(), sanitizeConfigValue(server.getUrl()),
					sanitizeConfigValue(server.getSseEndpoint()),
					server.getDescription(), server.isEnabled(), false, "",
					allowedTools(server), keyEnvName(server), keyConfigured(server),
					requiredEnvVars(server), serverSource(server), true, serverLocalOverride(server),
					effectiveHeaders(server) != null && !effectiveHeaders(server).isEmpty(),
					server.getApiKey() != null && !server.getApiKey().isBlank(),
					headerNames(server));
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

		private static String serverSource(McpProperties.McpServerInfo server) {
			if (server instanceof McpServerConfigService.ManagedMcpServerInfo managed) {
				return managed.getSource();
			}
			return "BUILTIN";
		}

		private static boolean serverLocalOverride(McpProperties.McpServerInfo server) {
			return server instanceof McpServerConfigService.ManagedMcpServerInfo managed
					&& managed.isLocalOverride();
		}

		private static Map<String, String> effectiveHeaders(McpProperties.McpServerInfo server) {
			return server.getHeaders() == null ? Map.of() : server.getHeaders();
		}

		private static List<String> headerNames(McpProperties.McpServerInfo server) {
			Map<String, String> headers = effectiveHeaders(server);
			if (headers.isEmpty()) {
				return List.of();
			}
			return headers.keySet().stream()
					.filter(name -> name != null && !name.isBlank())
					.map(String::strip)
					.distinct()
					.toList();
		}
	}

	public record McpConnectionTestResult(String id, String url, String sseEndpoint,
			boolean connected, int toolCount, List<String> toolNames, String error,
			long durationMs, List<String> requiredEnvVars, Boolean keyConfigured) {

		static McpConnectionTestResult connected(McpProperties.McpServerInfo server,
				List<String> toolNames, long durationMs) {
			List<String> names = toolNames == null ? List.of() : List.copyOf(toolNames);
			return new McpConnectionTestResult(server.getId(), sanitizeConfigValue(server.getUrl()),
					sanitizeConfigValue(server.getSseEndpoint()), true, names.size(), names, "", durationMs,
					ServerStatus.requiredEnvVars(server), ServerStatus.keyConfigured(server));
		}

		static McpConnectionTestResult failed(McpProperties.McpServerInfo server,
				String error, long durationMs) {
			return new McpConnectionTestResult(server.getId(), sanitizeConfigValue(server.getUrl()),
					sanitizeConfigValue(server.getSseEndpoint()), false, 0, List.of(),
					sanitizeSensitiveMessage(error), durationMs,
					ServerStatus.requiredEnvVars(server), ServerStatus.keyConfigured(server));
		}
	}

}
