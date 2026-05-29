package top.lanshan.manmu.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.config.McpProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class McpConfigMergeUtil {

    private static final Logger logger = LoggerFactory.getLogger(McpConfigMergeUtil.class);
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z0-9_]+)(?::([^}]*))?}");

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
        if (value == null || value.isBlank()) {
            return value;
        }
        Matcher matcher = ENV_PLACEHOLDER.matcher(value);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String envName = matcher.group(1);
            String fallback = matcher.group(2) != null ? matcher.group(2) : "";
            String envValue = System.getenv(envName);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(
                    envValue != null && !envValue.isBlank() ? envValue : fallback));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    public record NamedTransport(McpProperties.McpServerInfo server,
            WebFluxSseClientTransport transport) {
    }
}
