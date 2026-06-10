package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;
import top.lanshan.manmu.skill.market.SkillPackageArchiveService;
import top.lanshan.manmu.skill.market.SkillPackageType;
import top.lanshan.manmu.skill.plugin.JarSkillPackageLoader;
import top.lanshan.manmu.skill.plugin.SkillPluginRegistry;
import top.lanshan.manmu.skill.testsupport.JarSkillPackageTestSupport;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillToolProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void returnsEmptyWhenNoSkills() {
        SkillFileRepository fileRepo = new SkillFileRepository(
                java.nio.file.Path.of("/nonexistent"), objectMapper);
        SkillRegistry registry = new SkillRegistry(fileRepo);
        SkillToolProvider provider = new SkillToolProvider(registry, objectMapper);

        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertThat(callbacks).isEmpty();
    }

    @Test
    void returnsCallbacksOnlyForEnabledSkills() {
        SkillFileRepository fileRepo = new SkillFileRepository(
                java.nio.file.Path.of("/nonexistent"), objectMapper);
        SkillRegistry registry = new SkillRegistry(fileRepo);

        SkillDefinition enabled = new SkillDefinition();
        enabled.setName("enabled-skill");
        enabled.setDescription("E");
        enabled.setEnabled(true);
        registry.register(enabled, "prompt E");

        SkillDefinition disabled = new SkillDefinition();
        disabled.setName("disabled-skill");
        disabled.setDescription("D");
        disabled.setEnabled(false);
        registry.register(disabled, "prompt D");

        SkillToolProvider provider = new SkillToolProvider(registry, objectMapper);
        ToolCallback[] callbacks = provider.getToolCallbacks();

        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("skill__enabled_skill");
    }

    @Test
    void skipsSkillWithBlankTemplate() {
        SkillFileRepository fileRepo = new SkillFileRepository(
                java.nio.file.Path.of("/nonexistent"), objectMapper);
        SkillRegistry registry = new SkillRegistry(fileRepo);

        SkillDefinition def = new SkillDefinition();
        def.setName("no-template");
        def.setEnabled(true);
        registry.register(def, "");

        SkillToolProvider provider = new SkillToolProvider(registry, objectMapper);
        ToolCallback[] callbacks = provider.getToolCallbacks();

        assertThat(callbacks).isEmpty();
    }

    @Test
    void exposesBuiltInDemoPromptSkillsAsTools() {
        SkillFileRepository fileRepo = new SkillFileRepository(
                Path.of("src/main/java/top/lanshan/manmu/skill/content"), tempDir.resolve("local"), objectMapper);
        SkillRegistry registry = new SkillRegistry(fileRepo);
        registry.loadAll();

        SkillToolProvider provider = new SkillToolProvider(registry, fileRepo, objectMapper, null, false);
        ToolCallback[] callbacks = provider.getToolCallbacks();

        assertThat(Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name()))
                .contains("skill__weather_now", "skill__calculator", "skill__web_search");
        assertThat(Arrays.stream(callbacks)
                .filter(callback -> "skill__calculator".equals(callback.getToolDefinition().name()))
                .findFirst()
                .orElseThrow()
                .call("{\"expression\":\"(128 + 256) * 3 / 6\"}"))
                .contains("(128 + 256) * 3 / 6");
    }

    @Test
    void returnsJarSkillCallbackWhenJarPluginsAreEnabled() throws Exception {
        SkillFileRepository fileRepo = new SkillFileRepository(tempDir.resolve("builtin"),
                tempDir.resolve("local"), objectMapper);
        SkillRegistry registry = new SkillRegistry(fileRepo);
        SkillPluginRegistry pluginRegistry = new SkillPluginRegistry(
                new JarSkillPackageLoader(SkillPluginRegistry.class.getClassLoader()));
        try {
            SkillService service = new SkillService(fileRepo, registry, objectMapper,
                    new SkillPackageArchiveService(objectMapper), null, pluginRegistry, true);
            service.importJarPackage("echo-json-skill.zip",
                    JarSkillPackageTestSupport.jarSkillPackage(objectMapper, tempDir, "echo-json-skill", true));
            pluginRegistry.unregister("echo-json-skill");

            SkillToolProvider provider = new SkillToolProvider(registry, fileRepo, objectMapper, pluginRegistry, true);
            ToolCallback[] callbacks = provider.getToolCallbacks();

            assertThat(callbacks).hasSize(1);
            assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("skill__echo_json_skill");
            assertThat(callbacks[0].call("{\"message\":\"tool\"}"))
                    .contains("echo:tool")
                    .contains("SkillPluginClassLoader");
            assertThat(pluginRegistry.hasPlugin("echo-json-skill")).isTrue();
        } finally {
            pluginRegistry.close();
        }
    }

    @Test
    void skipsJarSkillCallbackWhenJarPluginsAreDisabled() {
        SkillFileRepository fileRepo = new SkillFileRepository(tempDir.resolve("local"), objectMapper);
        SkillRegistry registry = new SkillRegistry(fileRepo);
        SkillDefinition def = new SkillDefinition();
        def.setName("jar-disabled");
        def.setDescription("Disabled Jar");
        def.setEnabled(true);
        def.setPackageType(SkillPackageType.JAR);
        registry.register(def);

        SkillToolProvider provider = new SkillToolProvider(registry, fileRepo, objectMapper, null, false);

        assertThat(provider.getToolCallbacks()).isEmpty();
    }
}
