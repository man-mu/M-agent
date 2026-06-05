package top.lanshan.manmu.skill.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.lanshan.manmu.config.McpProperties;
import top.lanshan.manmu.mcp.McpServerConfigService;
import top.lanshan.manmu.mcp.McpToolProvider;
import top.lanshan.manmu.skill.market.SkillPackageType;
import top.lanshan.manmu.skill.service.SkillDefinition;
import top.lanshan.manmu.skill.service.SkillFileRepository;
import top.lanshan.manmu.skill.service.SkillService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SkillHealthService {

    private final SkillService skillService;
    private final SkillFileRepository fileRepository;
    private final McpToolProvider mcpToolProvider;
    private final McpServerConfigService mcpServerConfigService;
    private final ObjectMapper objectMapper;

    public SkillHealthService(SkillService skillService, SkillFileRepository fileRepository,
            McpToolProvider mcpToolProvider, ObjectMapper objectMapper) {
        this(skillService, fileRepository, mcpToolProvider, null, objectMapper);
    }

    public SkillHealthService(SkillService skillService, SkillFileRepository fileRepository,
            McpToolProvider mcpToolProvider, McpServerConfigService mcpServerConfigService,
            ObjectMapper objectMapper) {
        this.skillService = skillService;
        this.fileRepository = fileRepository;
        this.mcpToolProvider = mcpToolProvider;
        this.mcpServerConfigService = mcpServerConfigService;
        this.objectMapper = objectMapper;
    }

    public SkillHealthResult health(String name) {
        SkillDefinition definition = skillService.getDefinition(name)
                .orElseThrow(() -> new IllegalArgumentException("Skill '" + name + "' not found"));
        List<SkillHealthCheck> checks = new ArrayList<>();
        checks.add(new SkillHealthCheck("enabled", definition.isEnabled(),
                definition.isEnabled() ? "Skill is enabled" : "Skill is disabled"));
        checks.add(packageCheck(definition));
        checks.add(parameterSchemaCheck(definition));

        List<SkillDependencyHealth> dependencies = dependencyHealth(definition);
        boolean healthy = checks.stream().allMatch(SkillHealthCheck::healthy)
                && dependencies.stream().allMatch(SkillDependencyHealth::available);
        return new SkillHealthResult(definition.getName(), healthy,
                healthy ? "HEALTHY" : "DEGRADED", List.copyOf(checks),
                dependencies, Instant.now());
    }

    private SkillHealthCheck packageCheck(SkillDefinition definition) {
        if (definition.getPackageType() == SkillPackageType.JAR) {
            try {
                Path jar = fileRepository.packageDirectory(definition.getName()).resolve("plugin.jar");
                boolean present = Files.isRegularFile(jar);
                return new SkillHealthCheck("jarPackage", present,
                        present ? "plugin.jar is present" : "plugin.jar is missing");
            } catch (RuntimeException e) {
                return new SkillHealthCheck("jarPackage", false, safeMessage(e));
            }
        }
        String prompt = skillService.getPromptContent(definition.getName());
        boolean present = prompt != null && !prompt.isBlank();
        return new SkillHealthCheck("promptTemplate", present,
                present ? "Prompt template is present" : "Prompt template is missing");
    }

    private SkillHealthCheck parameterSchemaCheck(SkillDefinition definition) {
        if (definition.getParameters() == null) {
            return new SkillHealthCheck("parameterSchema", true, "No parameter schema configured");
        }
        try {
            objectMapper.readTree(definition.getInputSchemaJson());
            return new SkillHealthCheck("parameterSchema", true, "Parameter schema is valid JSON");
        } catch (Exception e) {
            return new SkillHealthCheck("parameterSchema", false, "Parameter schema is invalid JSON");
        }
    }

    private List<SkillDependencyHealth> dependencyHealth(SkillDefinition definition) {
        List<String> dependencies = definition.getDependencies() == null
                ? List.of()
                : definition.getDependencies();
        if (dependencies.isEmpty()) {
            return List.of();
        }
        List<McpToolProvider.ServerStatus> mcpStatuses = currentMcpStatuses();
        List<SkillDependencyHealth> result = new ArrayList<>();
        for (String dependency : dependencies) {
            if (dependency == null || dependency.isBlank()) {
                continue;
            }
            String normalized = dependency.strip();
            if (normalized.toLowerCase(Locale.ROOT).startsWith("mcp-")) {
                result.add(mcpDependencyHealth(normalized, mcpStatuses));
            } else {
                result.add(new SkillDependencyHealth(normalized, "UNKNOWN", false,
                        "Unsupported dependency type", List.of(), List.of(), null));
            }
        }
        return List.copyOf(result);
    }

    private SkillDependencyHealth mcpDependencyHealth(String dependency,
            List<McpToolProvider.ServerStatus> statuses) {
        if (mcpServerConfigService != null) {
            return mcpDependencyHealthFromConfig(dependency);
        }
        List<McpToolProvider.ServerStatus> matched = statuses.stream()
                .filter(status -> matchesMcpDependency(dependency, status))
                .toList();
        boolean available = matched.stream().anyMatch(McpToolProvider.ServerStatus::connected);
        List<String> matchedServers = matched.stream()
                .map(status -> firstPresent(status.id(), status.url()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> requiredEnvVars = matched.stream()
                .flatMap(status -> status.requiredEnvVars().stream())
                .distinct()
                .toList();
        Boolean keyConfigured = matched.stream()
                .map(McpToolProvider.ServerStatus::keyConfigured)
                .filter(Objects::nonNull)
                .reduce((left, right) -> left && right)
                .orElse(null);
        String message;
        if (matched.isEmpty()) {
            message = "No matching MCP server configured";
        } else if (available) {
            message = "MCP dependency is connected";
        } else {
            message = matched.stream()
                    .map(McpToolProvider.ServerStatus::error)
                    .filter(error -> error != null && !error.isBlank())
                    .findFirst()
                    .orElse("MCP dependency is not connected");
        }
        return new SkillDependencyHealth(dependency, "MCP", available, message,
                matchedServers, requiredEnvVars, keyConfigured);
    }

    private SkillDependencyHealth mcpDependencyHealthFromConfig(String dependency) {
        List<McpProperties.McpServerInfo> servers = configuredMcpServers().stream()
                .filter(server -> matchesMcpDependency(dependency, server))
                .toList();
        if (servers.isEmpty()) {
            return new SkillDependencyHealth(dependency, "MCP", false,
                    "No matching MCP server configured", List.of(), List.of(), null);
        }
        List<McpToolProvider.McpConnectionTestResult> results = servers.stream()
                .map(this::testMcpServer)
                .toList();
        boolean available = results.stream().anyMatch(McpToolProvider.McpConnectionTestResult::connected);
        List<String> matchedServers = results.stream()
                .map(result -> firstPresent(result.id(), result.url()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> requiredEnvVars = results.stream()
                .flatMap(result -> result.requiredEnvVars().stream())
                .distinct()
                .toList();
        Boolean keyConfigured = results.stream()
                .map(McpToolProvider.McpConnectionTestResult::keyConfigured)
                .filter(Objects::nonNull)
                .reduce((left, right) -> left && right)
                .orElse(null);
        String message = available
                ? "MCP dependency is connected"
                : results.stream()
                        .map(McpToolProvider.McpConnectionTestResult::error)
                        .filter(error -> error != null && !error.isBlank())
                        .findFirst()
                        .orElse("MCP dependency is not connected");
        return new SkillDependencyHealth(dependency, "MCP", available, message,
                matchedServers, requiredEnvVars, keyConfigured);
    }

    private List<McpProperties.McpServerInfo> configuredMcpServers() {
        try {
            McpProperties.McpServerConfig config = mcpServerConfigService.currentConfig();
            return config == null || config.getMcpServers() == null ? List.of() : config.getMcpServers();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private McpToolProvider.McpConnectionTestResult testMcpServer(McpProperties.McpServerInfo server) {
        if (!server.isEnabled()) {
            return new McpToolProvider.McpConnectionTestResult(server.getId(), server.getUrl(),
                    server.getSseEndpoint(), false, 0, List.of(), "MCP server is disabled",
                    0, List.of(), null);
        }
        if (mcpToolProvider == null) {
            return new McpToolProvider.McpConnectionTestResult(server.getId(), server.getUrl(),
                    server.getSseEndpoint(), false, 0, List.of(), "MCP provider is not available",
                    0, List.of(), null);
        }
        return mcpToolProvider.testConnection(server);
    }

    private List<McpToolProvider.ServerStatus> currentMcpStatuses() {
        if (mcpToolProvider == null) {
            return List.of();
        }
        try {
            McpToolProvider.McpStatus status = mcpToolProvider.reload();
            return status == null || status.servers() == null ? List.of() : status.servers();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private boolean matchesMcpDependency(String dependency, McpToolProvider.ServerStatus status) {
        String needle = dependency.toLowerCase(Locale.ROOT);
        String suffix = needle.substring("mcp-".length());
        if (contains(status.id(), needle) || contains(status.id(), suffix)) {
            return true;
        }
        if (contains(status.description(), suffix) || contains(status.url(), suffix)) {
            return true;
        }
        List<String> tools = status.allowedTools() == null ? List.of() : status.allowedTools();
        if ("qweather".equals(suffix) && tools.contains("weather_now")) {
            return true;
        }
        if ("amap".equals(suffix) && tools.stream().anyMatch(tool -> tool.startsWith("maps_"))) {
            return true;
        }
        return tools.stream().anyMatch(tool -> contains(tool, suffix));
    }

    private boolean matchesMcpDependency(String dependency, McpProperties.McpServerInfo server) {
        String needle = dependency.toLowerCase(Locale.ROOT);
        String suffix = needle.substring("mcp-".length());
        if (contains(server.getId(), needle) || contains(server.getId(), suffix)) {
            return true;
        }
        if (contains(server.getDescription(), suffix) || contains(server.getUrl(), suffix)) {
            return true;
        }
        List<String> tools = server.getAllowedTools() == null ? List.of() : server.getAllowedTools();
        if ("qweather".equals(suffix) && tools.contains("weather_now")) {
            return true;
        }
        if ("amap".equals(suffix) && tools.stream().anyMatch(tool -> tool.startsWith("maps_"))) {
            return true;
        }
        return tools.stream().anyMatch(tool -> contains(tool, suffix));
    }

    private boolean contains(String value, String fragment) {
        return value != null && fragment != null
                && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String safeMessage(Throwable e) {
        if (e == null) {
            return "Unknown error";
        }
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }
}
