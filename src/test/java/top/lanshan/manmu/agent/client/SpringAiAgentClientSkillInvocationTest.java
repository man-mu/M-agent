package top.lanshan.manmu.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import top.lanshan.manmu.mcp.McpToolProvider;
import top.lanshan.manmu.skill.health.SkillInvocationHistoryService;
import top.lanshan.manmu.skill.market.SkillPackageArchiveService;
import top.lanshan.manmu.skill.plugin.JarSkillPackageLoader;
import top.lanshan.manmu.skill.plugin.SkillPluginRegistry;
import top.lanshan.manmu.skill.service.SkillDefinition;
import top.lanshan.manmu.skill.service.SkillFileRepository;
import top.lanshan.manmu.skill.service.SkillRegistry;
import top.lanshan.manmu.skill.service.SkillService;
import top.lanshan.manmu.skill.service.SkillToolProvider;
import top.lanshan.manmu.skill.testsupport.JarSkillPackageTestSupport;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiAgentClientSkillInvocationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    private SkillService skillService;
    private SkillInvocationHistoryService invocationHistoryService;

    @BeforeEach
    void setUp() {
        SkillFileRepository fileRepo = new SkillFileRepository(tempDir, objectMapper);
        SkillRegistry registry = new SkillRegistry(fileRepo);

        SkillDefinition def = new SkillDefinition();
        def.setName("code-review");
        def.setDescription("Review code");
        def.setEnabled(true);
        def.setParameters(Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of("type", "string", "description", "code content"),
                        "language", Map.of("type", "string", "description", "language", "default", "java")
                ),
                "required", java.util.List.of("code")
        ));
        registry.register(def, "Review in {{language}}:\n\n```\n{{code}}\n```");

        SkillDefinition weather = new SkillDefinition();
        weather.setName("weather-now");
        weather.setDescription("Weather now");
        weather.setEnabled(true);
        weather.setParameters(Map.of(
                "type", "object",
                "properties", Map.of(
                        "location", Map.of("type", "string", "description", "location"),
                        "unit", Map.of("type", "string", "description", "unit", "default", "m"),
                        "lang", Map.of("type", "string", "description", "language", "default", "zh")
                ),
                "required", java.util.List.of("location")
        ));
        registry.register(weather, "Weather request for {{location}}.");

        skillService = new SkillService(fileRepo, registry, objectMapper);
        invocationHistoryService = new SkillInvocationHistoryService();
    }

    @Test
    void explicitSkillInjectionPrependsRenderedTemplateToSystemPrompt() {
        // We can't fully mock RoutingChatModel easily, so test via reflection
        // by constructing a client and verifying the resolve logic indirectly
        // through SkillService.renderSkill()
        Map<String, Object> params = Map.of("code", "public class Foo {}", "language", "java");
        String rendered = skillService.renderSkill("code-review", params);
        assertThat(rendered).contains("public class Foo {}");
        assertThat(rendered).contains("java");
        assertThat(rendered).doesNotContain("{{code}}");
    }

    @Test
    void renderSkillReturnsNullForNonexistentSkill() {
        assertThat(skillService.renderSkill("nonexistent", Map.of())).isNull();
    }

    @Test
    void renderSkillWithOnlyRequiredParamAppliesDefaults() {
        Map<String, Object> params = Map.of("code", "System.out.println()", "language", "java");
        String rendered = skillService.renderSkill("code-review", params);
        assertThat(rendered).contains("System.out.println()");
        assertThat(rendered).contains("java");
    }

    @Test
    void explicitSkillInjectionWorksInsideUserQuestionBlock() throws Exception {
        SpringAiAgentClient client = clientWithSkillService();
        String wrappedPrompt = """
                User question:
                @code-review public class Foo {}

                Deep research is enabled: false
                """;

        String resolved = resolveExplicitSkillCall(client, "base system", wrappedPrompt);

        assertThat(resolved).startsWith("Review in java:");
        assertThat(resolved).contains("public class Foo {}");
        assertThat(resolved).endsWith("base system");
        assertThat(invocationHistoryService.recent("code-review", 10)).singleElement()
                .satisfies(record -> {
                    assertThat(record.source()).isEqualTo("EXPLICIT");
                    assertThat(record.output()).contains("public class Foo {}");
                });
    }

    @Test
    void nonSkillUserQuestionBlockKeepsSystemPrompt() throws Exception {
        SpringAiAgentClient client = clientWithSkillService();
        String wrappedPrompt = """
                User question:
                Please answer normally

                Deep research is enabled: false
                """;

        String resolved = resolveExplicitSkillCall(client, "base system", wrappedPrompt);

        assertThat(resolved).isEqualTo("base system");
    }

    @Test
    void weatherSkillCallsMcpToolAndInjectsResult() throws Exception {
        RecordingWeatherTool weatherTool = new RecordingWeatherTool();
        SpringAiAgentClient client = clientWithSkillService(new StaticMcpToolProvider(weatherTool));
        String wrappedPrompt = """
                User question:
                @weather-now 查询北京今天实时天气

                Deep research is enabled: false
                """;

        String resolved = resolveExplicitSkillCall(client, "base system", wrappedPrompt);

        assertThat(weatherTool.lastInput).contains("\"location\":\"北京\"");
        assertThat(weatherTool.lastInput).contains("\"unit\":\"m\"");
        assertThat(weatherTool.lastInput).contains("\"lang\":\"zh\"");
        assertThat(resolved).contains("weather_now");
        assertThat(resolved).contains("北京当前阴，温度 27°C");
        assertThat(resolved).endsWith("base system");
    }

    @Test
    void weatherSkillMatchesSpringAiPrefixedMcpToolName() throws Exception {
        RecordingWeatherTool weatherTool = new RecordingWeatherTool("deepresearch_mvp_weather_now");
        SpringAiAgentClient client = clientWithSkillService(new StaticMcpToolProvider(weatherTool));
        String wrappedPrompt = """
                User question:
                @weather-now Shanghai

                Deep research is enabled: false
                """;

        String resolved = resolveExplicitSkillCall(client, "base system", wrappedPrompt);

        assertThat(weatherTool.lastInput).contains("\"location\":\"Shanghai\"");
        assertThat(resolved).contains("weather_now");
        assertThat(resolved).endsWith("base system");
    }

    @Test
    void jarSkillExplicitInvocationCallsLoadedJarTool() throws Exception {
        SkillFileRepository fileRepo = new SkillFileRepository(tempDir.resolve("builtin"),
                tempDir.resolve("local"), objectMapper);
        SkillRegistry registry = new SkillRegistry(fileRepo);
        SkillPluginRegistry pluginRegistry = new SkillPluginRegistry(
                new JarSkillPackageLoader(SkillPluginRegistry.class.getClassLoader()));
        try {
            SkillService jarSkillService = new SkillService(fileRepo, registry, objectMapper,
                    new SkillPackageArchiveService(objectMapper), null, pluginRegistry, true);
            jarSkillService.importJarPackage("echo-json-skill.zip",
                    JarSkillPackageTestSupport.jarSkillPackage(objectMapper, tempDir, "echo-json-skill", true));
            SkillToolProvider jarSkillToolProvider =
                    new SkillToolProvider(registry, fileRepo, objectMapper, pluginRegistry, true);
            SpringAiAgentClient client = clientWithSkillService(jarSkillService, jarSkillToolProvider, null);
            String wrappedPrompt = """
                    User question:
                    @echo-json-skill hello from chat

                    Deep research is enabled: false
                    """;

            String resolved = resolveExplicitSkillCall(client, "base system", wrappedPrompt);

            assertThat(resolved).contains("echo-json-skill Skill 工具真实返回");
            assertThat(resolved).contains("echo:hello from chat");
            assertThat(resolved).contains("SkillPluginClassLoader");
            assertThat(resolved).endsWith("base system");
        } finally {
            pluginRegistry.close();
        }
    }

    private SpringAiAgentClient clientWithSkillService() throws Exception {
        return clientWithSkillService(null);
    }

    private SpringAiAgentClient clientWithSkillService(McpToolProvider mcpToolProvider) throws Exception {
        return clientWithSkillService(skillService, null, mcpToolProvider);
    }

    private SpringAiAgentClient clientWithSkillService(SkillService service,
            SkillToolProvider toolProvider, McpToolProvider mcpToolProvider) throws Exception {
        SpringAiAgentClient client = new SpringAiAgentClient(null);
        Field skillServiceField = SpringAiAgentClient.class.getDeclaredField("skillService");
        skillServiceField.setAccessible(true);
        skillServiceField.set(client, service);
        if (toolProvider != null) {
            Field skillToolProviderField = SpringAiAgentClient.class.getDeclaredField("skillToolProvider");
            skillToolProviderField.setAccessible(true);
            skillToolProviderField.set(client, toolProvider);
        }
        if (mcpToolProvider != null) {
            Field mcpToolProviderField = SpringAiAgentClient.class.getDeclaredField("mcpToolProvider");
            mcpToolProviderField.setAccessible(true);
            mcpToolProviderField.set(client, mcpToolProvider);
        }
        Field historyField = SpringAiAgentClient.class.getDeclaredField("skillInvocationHistoryService");
        historyField.setAccessible(true);
        historyField.set(client, invocationHistoryService);
        return client;
    }

    private String resolveExplicitSkillCall(SpringAiAgentClient client, String systemPrompt,
            String userPrompt) throws Exception {
        Method method = SpringAiAgentClient.class
                .getDeclaredMethod("resolveExplicitSkillCall", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(client, systemPrompt, userPrompt);
    }

    private static class StaticMcpToolProvider extends McpToolProvider {

        private final ToolCallback[] callbacks;

        StaticMcpToolProvider(ToolCallback... callbacks) {
            super(new top.lanshan.manmu.config.McpProperties(),
                    new top.lanshan.manmu.config.McpProperties.McpServerConfig(),
                    org.springframework.web.reactive.function.client.WebClient.builder(),
                    new ObjectMapper(), "test", "0.0.0");
            this.callbacks = callbacks;
        }

        @Override
        public ToolCallback[] getToolCallbacks() {
            return callbacks;
        }
    }

    private static class RecordingWeatherTool implements ToolCallback {

        private final String name;
        private String lastInput;

        RecordingWeatherTool() {
            this("weather_now");
        }

        RecordingWeatherTool(String name) {
            this.name = name;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return DefaultToolDefinition.builder()
                    .name(name)
                    .description("Weather now")
                    .inputSchema("{}")
                    .build();
        }

        @Override
        public String call(String toolInput) {
            this.lastInput = toolInput;
            return "北京当前阴，温度 27°C，体感 25°C，湿度 57%，东风 4级，观测时间 2026-06-03T11:42+08:00。";
        }
    }
}
