package top.lanshan.manmu.skill.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.config.McpProperties;
import top.lanshan.manmu.mcp.McpServerConfigService;
import top.lanshan.manmu.mcp.McpToolProvider;
import top.lanshan.manmu.modelprovider.ModelProviderKeyStore;
import top.lanshan.manmu.search.BochaSearchProperties;
import top.lanshan.manmu.skill.market.SkillPackageType;
import top.lanshan.manmu.skill.service.SkillDefinition;
import top.lanshan.manmu.skill.service.SkillFileRepository;
import top.lanshan.manmu.skill.service.SkillRegistry;
import top.lanshan.manmu.skill.service.SkillService;

import java.nio.file.Path;
import java.time.Duration;
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
                List.of("QWEATHER_API_KEY"), "LOCAL", true, true,
                false, false, List.of())));

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
                List.of("QWEATHER_API_KEY"), "LOCAL", true, true,
                false, false, List.of())));

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

    @Test
    void usesShortConnectionTestForConfiguredMcpDependencyHealth() throws Exception {
        SkillFileRepository repository = new SkillFileRepository(tempDir, objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        SkillService skillService = new SkillService(repository, registry, objectMapper);
        SkillDefinition definition = new SkillDefinition();
        definition.setName("weather-now");
        definition.setDescription("Weather now");
        definition.setDependencies(List.of("mcp-qweather"));
        skillService.create(definition, "Weather {{location}}");

        McpProperties.McpServerInfo server = new McpProperties.McpServerInfo();
        server.setId("local-qweather");
        server.setUrl("http://127.0.0.1:18090");
        server.setSseEndpoint("/sse");
        server.setDescription("本地和风天气 MCP");
        server.setAllowedTools(List.of("weather_now"));
        McpServerConfigService configService = new McpServerConfigService(
                new McpProperties.McpServerConfig(List.of(server)),
                tempDir.resolve("mcp-servers.json"), objectMapper);
        ConfiguredMcpToolProvider mcpProvider = new ConfiguredMcpToolProvider();

        SkillHealthResult result = new SkillHealthService(skillService, repository, mcpProvider,
                configService, objectMapper).health("weather-now");

        assertThat(result.healthy()).isFalse();
        assertThat(result.dependencies()).singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.name()).isEqualTo("mcp-qweather");
                    assertThat(dependency.message()).isEqualTo("MCP server is not reachable");
                    assertThat(dependency.requiredEnvVars()).contains("QWEATHER_API_KEY");
                });
        assertThat(mcpProvider.lastTimeout).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void reportsSearchDependencyAvailabilityWithoutExposingSecrets() throws Exception {
        SkillFileRepository repository = new SkillFileRepository(tempDir.resolve("skills"), objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        SkillService skillService = new SkillService(repository, registry, objectMapper);
        SkillDefinition definition = new SkillDefinition();
        definition.setName("web-search");
        definition.setDescription("Search the web");
        definition.setEnabled(true);
        definition.setDependencies(List.of("search-bocha"));
        skillService.create(definition, "Search {{query}}");

        BochaSearchProperties properties = new BochaSearchProperties();
        properties.setEndpoint("https://api.bochaai.com/v1/web-search");
        properties.setApiKey("secret-bocha-key");
        ModelProviderKeyStore keyStore = new ModelProviderKeyStore(tempDir.resolve("keys.json"), objectMapper);

        SkillHealthResult result = new SkillHealthService(skillService, repository, null, null,
                properties, keyStore, objectMapper).health("web-search");

        assertThat(result.healthy()).isTrue();
        assertThat(result.dependencies()).singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.name()).isEqualTo("search-bocha");
                    assertThat(dependency.type()).isEqualTo("SEARCH");
                    assertThat(dependency.available()).isTrue();
                    assertThat(dependency.requiredEnvVars()).contains("BOCHA_API_KEY");
                    assertThat(dependency.keyConfigured()).isTrue();
                    assertThat(dependency.toString()).doesNotContain("secret-bocha-key");
                });
    }

    @Test
    void reportsJarPluginSwitchForJarSkills() throws Exception {
        SkillFileRepository repository = new SkillFileRepository(tempDir.resolve("skills"), objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        SkillService skillService = new SkillService(repository, registry, objectMapper);
        SkillDefinition definition = new SkillDefinition();
        definition.setName("echo-json-skill");
        definition.setDescription("Echo JSON Jar Skill");
        definition.setEnabled(true);
        definition.setPackageType(SkillPackageType.JAR);
        repository.writeJarSkill("echo-json-skill", definition, new byte[] { 1, 2, 3 }, "Trusted local demo");
        registry.loadAll();

        SkillHealthResult result = new SkillHealthService(skillService, repository, null, null,
                null, null, objectMapper, false).health("echo-json-skill");

        assertThat(result.healthy()).isFalse();
        assertThat(result.checks()).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("jarPlugins");
            assertThat(check.healthy()).isFalse();
            assertThat(check.message()).isEqualTo("Jar Skill plugins are disabled");
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

    private static class ConfiguredMcpToolProvider extends McpToolProvider {

        private Duration lastTimeout;

        ConfiguredMcpToolProvider() {
            super(new McpProperties(), new McpProperties.McpServerConfig(),
                    WebClient.builder(), new ObjectMapper(), "test", "0.0.0");
        }

        @Override
        public McpConnectionTestResult testConnection(McpProperties.McpServerInfo server, Duration timeout) {
            this.lastTimeout = timeout;
            return new McpConnectionTestResult(server.getId(), server.getUrl(), server.getSseEndpoint(),
                    false, 0, List.of(), "MCP server is not reachable", 10,
                    List.of("QWEATHER_API_KEY"), false);
        }
    }
}
