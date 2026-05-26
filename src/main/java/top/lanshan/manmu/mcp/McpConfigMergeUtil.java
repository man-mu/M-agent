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

public final class McpConfigMergeUtil {

    private static final Logger logger = LoggerFactory.getLogger(McpConfigMergeUtil.class);

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

        List<WebFluxSseClientTransport> transports = new ArrayList<>();
        if (config == null || config.getMcpServers() == null) {
            return transports;
        }

        for (McpProperties.McpServerInfo si : config.getMcpServers()) {
            if (!si.isEnabled()) {
                continue;
            }
            WebClient.Builder clone = webClientBuilder.clone().baseUrl(si.getUrl());
            String sseEndpoint = si.getSseEndpoint() != null ? si.getSseEndpoint() : "/sse";
            WebFluxSseClientTransport transport = WebFluxSseClientTransport.builder(clone)
                .sseEndpoint(sseEndpoint)
                .objectMapper(objectMapper)
                .build();
            transports.add(transport);
            logger.info("MCP transport created: {} -> {}", si.getUrl(), sseEndpoint);
        }
        return transports;
    }
}
