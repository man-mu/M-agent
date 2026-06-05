package top.lanshan.manmu.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.mcp.McpServerConfigService;
import top.lanshan.manmu.mcp.McpToolProvider;

@Configuration
@ConditionalOnProperty(prefix = "mvp.mcp", name = "enabled", havingValue = "true")
public class McpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(McpConfiguration.class);

    @Bean
    McpProperties.McpServerConfig staticMcpServerConfig(McpProperties mcpProperties,
            ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        String location = mcpProperties.getConfigLocation();
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            logger.warn("MCP config not found at {}, using empty config", location);
            return new McpProperties.McpServerConfig();
        }
        try {
            return objectMapper.readValue(resource.getInputStream(),
                    new TypeReference<McpProperties.McpServerConfig>() {});
        } catch (Exception e) {
            logger.error("Failed to load MCP config from {}: {}", location, e.getMessage());
            return new McpProperties.McpServerConfig();
        }
    }

    @Bean
    McpServerConfigService mcpServerConfigService(McpProperties.McpServerConfig staticConfig,
            McpProperties mcpProperties, ObjectMapper objectMapper) {
        return new McpServerConfigService(staticConfig, mcpProperties, objectMapper);
    }

    @Bean
    McpToolProvider mcpToolProvider(McpProperties mcpProperties,
            McpServerConfigService configService,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {

        return new McpToolProvider(mcpProperties, configService::currentConfig, webClientBuilder,
                objectMapper, "deepresearch-mvp", "0.1.0");
    }
}
