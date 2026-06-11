package top.lanshan.manmu.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.config.McpProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class McpConfigMergeUtil {

    private static final Logger logger = LoggerFactory.getLogger(McpConfigMergeUtil.class);
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z0-9_]+)(?::([^}]*))?}");
    private static final Path LOCAL_MCP_KEYS_PATH = Path.of(".local", "mcp-keys.json");
    private static final ObjectMapper LOCAL_KEYS_OBJECT_MAPPER = new ObjectMapper();
    private static volatile Map<String, String> localMcpKeysCache;

    private McpConfigMergeUtil() {}

    public static McpProperties.McpServerConfig merge(McpProperties.McpServerConfig staticConfig,
            Map<String, Object> runtimeMcpSettings, ObjectMapper objectMapper) {

        Map<String, McpProperties.McpServerInfo> merged = new LinkedHashMap<>();
        if (staticConfig != null && staticConfig.getMcpServers() != null) {
            for (McpProperties.McpServerInfo si : staticConfig.getMcpServers()) {
                merged.put(si.getUrl(), si);
            }
        }

        if (runtimeMcpSettings != null && !runtimeMcpSettings.isEmpty()) {
            try {
                McpProperties.McpServerConfig runtimeConfig = objectMapper.convertValue(
                        runtimeMcpSettings, new TypeReference<McpProperties.McpServerConfig>() {});
                if (runtimeConfig.getMcpServers() != null) {
                    for (McpProperties.McpServerInfo si : runtimeConfig.getMcpServers()) {
                        merged.put(si.getUrl(), si);
                        logger.info("MCP runtime override: {} (enabled={})", si.getUrl(), si.isEnabled());
                    }
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Failed to parse runtime mcp_settings, using static config only: {}", e.getMessage());
            }
        }

        return new McpProperties.McpServerConfig(new ArrayList<>(merged.values()));
    }

    public static List<WebFluxSseClientTransport> createTransports(McpProperties.McpServerConfig config,
            WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        return createNamedTransports(config, webClientBuilder, objectMapper).stream()
                .map(NamedTransport::transport)
                .toList();
    }

    public static List<NamedTransport> createNamedTransports(McpProperties.McpServerConfig config,
            WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {

        List<NamedTransport> transports = new ArrayList<>();
        if (config == null || config.getMcpServers() == null) {
            return transports;
        }

        for (McpProperties.McpServerInfo si : config.getMcpServers()) {
            if (!si.isEnabled()) {
                continue;
            }
            WebClient.Builder clone = webClientBuilder.clone().baseUrl(resolvePlaceholders(si.getUrl()));

            Map<String, String> headers = resolveHeaders(si);
            boolean hasAuth = hasAuthorizationHeader(headers);
            headers.forEach(clone::defaultHeader);

            if (!hasAuth && si.getApiKey() != null && !si.getApiKey().isBlank()) {
                clone.defaultHeader("Authorization", "Bearer " + resolvePlaceholders(si.getApiKey()));
            }

            String sseEndpoint = si.getSseEndpoint() != null ? si.getSseEndpoint() : "/sse";
            sseEndpoint = resolvePlaceholders(sseEndpoint);
            WebFluxSseClientTransport transport = WebFluxSseClientTransport.builder(clone)
                .sseEndpoint(sseEndpoint)
                .objectMapper(objectMapper)
                .build();
            transports.add(new NamedTransport(si, transport));
            logger.info("MCP transport created: {} -> {}", si.getUrl(), sseEndpoint);
        }
        return transports;
    }

    static String resolvePlaceholders(String value) {
        return resolvePlaceholders(value, localMcpKeys());
    }

    static String resolvePlaceholders(String value, Map<String, String> localKeys) {
        if (value == null || value.isBlank()) {
            return value;
        }
        Matcher matcher = ENV_PLACEHOLDER.matcher(value);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String envName = matcher.group(1);
            String fallback = matcher.group(2) != null ? matcher.group(2) : "";
            String envValue = System.getenv(envName);
            String localValue = localKeys != null ? localKeys.get(envName) : null;
            String resolvedValue = firstPresent(envValue, localValue, fallback);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(
                    resolvedValue));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    static List<String> placeholderNames(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        Matcher matcher = ENV_PLACEHOLDER.matcher(value);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            String envName = matcher.group(1);
            if (!names.contains(envName)) {
                names.add(envName);
            }
        }
        return List.copyOf(names);
    }

    static boolean hasConfiguredValue(String envName) {
        if (envName == null || envName.isBlank()) {
            return false;
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return true;
        }
        String localValue = localMcpKeys().get(envName);
        return localValue != null && !localValue.isBlank();
    }

    static Map<String, String> readLocalMcpKeys(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return Map.of();
        }
        try (InputStream input = Files.newInputStream(path)) {
            Map<String, Object> raw = LOCAL_KEYS_OBJECT_MAPPER.readValue(input,
                    new TypeReference<Map<String, Object>>() {});
            Map<String, String> keys = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (key != null && value instanceof String stringValue && !stringValue.isBlank()) {
                    keys.put(key, stringValue);
                }
            });
            return Map.copyOf(keys);
        } catch (IOException e) {
            logger.warn("Failed to read local MCP key file {} ({})", path, e.getClass().getSimpleName());
            return Map.of();
        }
    }

    private static Map<String, String> localMcpKeys() {
        Map<String, String> cached = localMcpKeysCache;
        if (cached != null) {
            return cached;
        }
        synchronized (McpConfigMergeUtil.class) {
            if (localMcpKeysCache == null) {
                localMcpKeysCache = readLocalMcpKeys(LOCAL_MCP_KEYS_PATH);
            }
            return localMcpKeysCache;
        }
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    static Map<String, String> resolveHeaders(McpProperties.McpServerInfo serverInfo) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (serverInfo.getHeaders() == null || serverInfo.getHeaders().isEmpty()) {
            return resolved;
        }
        serverInfo.getHeaders().forEach((name, value) -> {
            if (name != null && !name.isBlank() && value != null) {
                resolved.put(name.strip(), resolvePlaceholders(value));
            }
        });
        return resolved;
    }

    static boolean hasAuthorizationHeader(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        return headers.keySet().stream()
                .anyMatch(k -> "Authorization".equalsIgnoreCase(k));
    }

    public record NamedTransport(McpProperties.McpServerInfo server,
            WebFluxSseClientTransport transport) {
    }
}
