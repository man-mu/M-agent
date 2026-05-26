package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillToolProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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
}
