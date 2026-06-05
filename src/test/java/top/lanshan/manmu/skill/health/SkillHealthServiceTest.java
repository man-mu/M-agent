package top.lanshan.manmu.skill.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.config.McpProperties;
import top.lanshan.manmu.mcp.McpToolProvider;
import top.lanshan.manmu.skill.service.SkillDefinition;
import top.lanshan.manmu.skill.service.SkillFileRepository;
import top.lanshan.manmu.skill.service.SkillRegistry;
import top.lanshan.manmu.skill.service.SkillService;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillHealthServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void reportsHealthyPromptSkillWhenTemplateAndSchemaArePresent() throws Exception {
        SkillFileRepository repository = new SkillFileRepository(tempDir, objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        SkillService skillService = new SkillService(repository, registry, objectMapper);
        SkillDefinition definition = new SkillDefinition();
        definition.setName("code-review");
        definition.setDescription("Review code");
        definition.setEnabled(true);
        definition.setParameters(Map.of("type", "object"));
        skillService.create(definition, "Review {{code}}");

        SkillHealthResult result = new SkillHealthService(skillService, repository, null, objectMapper)
                .health("code-review");

        assertThat(result.healthy()).isTrue();
        assertThat(result.status()).isEqualTo("HEALTHY");
        assertThat(result.checks()).extracting(SkillHealthCheck::name)
                .contains("enabled", "promptTemplate", "parameterSchema");
        assertThat(result.dependencies()).isEmpty();
    }

    @Test
    void reportsMcpDependencyAvailabilityWithoutExposingSecrets() throws Exception {
        SkillFileRepository repository = new SkillFileRepository(tempDir, objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        SkillService skillService = new SkillService(repository, registry, objectMapper);
        SkillDefinition definition = new SkillDefinition();
        definition.setName("weather-now");
        definition.setDescription("Weather now");
        definition.setEnabled(true);
        definition.setDependencies(List.of("mcp-qweather"));
        skillService.create(definition, "Weather {{location}}");
        McpToolProvider mcpProvider = new StaticMcpToolProvider(List.of(new McpToolProvider.ServerStatus(
                "local-qweather", "http://127.0.0.1:18090", "/sse",
                "本地和风天气 MCP", true, true, "",
                List.of("weather_now"), "QWEATHER_API_KEY", true,
                List.of("QWEATHER_API_KEY"), "LOCAL", true, true)));

        SkillHealthResult result = new SkillHealthService(skillService, repository, mcpProvider, objectMapper)
                .health("weather-now");

        assertThat(result.healthy()).isTrue();
        assertThat(result.dependencies()).singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.name()).isEqualTo("mcp-qweather");
                    assertThat(dependency.available()).isTrue();
                    assertThat(dependency.requiredEnvVars()).contains("QWEATHER_API_KEY");
                    assertThat(dependency.keyConfigured()).isTrue();
                    assertThat(dependency.toString()).doesNotContain("secret");
                });
    }

    @Test
    void reportsDegradedWhenMcpDependencyIsUnavailable() throws Exception {
        SkillFileRepository repository = new SkillFileRepository(tempDir, objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        SkillService skillService = new SkillService(repository, registry, objectMapper);
        SkillDefinition definition = new SkillDefinition();
        definition.setName("weather-now");
        definition.setDescription("Weather now");
        definition.setDependencies(List.of("mcp-qweather"));
        skillService.create(definition, "Weather {{location}}");
        McpToolProvider mcpProvider = new StaticMcpToolProvider(List.of(new McpToolProvider.ServerStatus(
                "local-qweather", "http://127.0.0.1:18090", "/sse",
                "本地和风天气 MCP", true, false, "MCP server is not reachable",
                List.of("weather_now"), "QWEATHER_API_KEY", false,
                List.of("QWEATHER_API_KEY"), "LOCAL", true, true)));

        SkillHealthResult result = new SkillHealthService(skillService, repository, mcpProvider, objectMapper)
                .health("weather-now");

        assertThat(result.healthy()).isFalse();
        assertThat(result.status()).isEqualTo("DEGRADED");
        assertThat(result.dependencies()).singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.available()).isFalse();
                    assertThat(dependency.message()).isEqualTo("MCP server is not reachable");
                });
    }

    private static class StaticMcpToolProvider extends McpToolProvider {

        private final List<ServerStatus> statuses;

        StaticMcpToolProvider(List<ServerStatus> statuses) {
            super(new McpProperties(), new McpProperties.McpServerConfig(),
                    WebClient.builder(), new ObjectMapper(), "test", "0.0.0");
            this.statuses = statuses;
        }

        @Override
        public McpStatus getStatus() {
            return new McpStatus(true, statuses, 1);
        }
    }
}
