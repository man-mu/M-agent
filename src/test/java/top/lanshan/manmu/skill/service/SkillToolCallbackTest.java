package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillToolCallbackTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void toolNameUsesSkillPrefix() {
        SkillDefinition def = new SkillDefinition();
        def.setName("code-review");
        def.setDescription("Review code");
        def.setParameters(Map.of("type", "object"));

        SkillToolCallback callback = new SkillToolCallback(def, "Review: {{code}}", objectMapper);

        ToolDefinition toolDef = callback.getToolDefinition();
        assertThat(toolDef.name()).isEqualTo("skill__code_review");
        assertThat(toolDef.description()).isEqualTo("Review code");
    }

    @Test
    void rendersTemplateWithParams() {
        SkillDefinition def = new SkillDefinition();
        def.setName("test");
        def.setParameters(Map.of("type", "object"));

        SkillToolCallback callback = new SkillToolCallback(def,
                "Hello {{name}}, your code is: {{code}}", objectMapper);

        String result = callback.call("{\"name\": \"Alice\", \"code\": \"System.out.println()\"}");
        assertThat(result).isEqualTo("Hello Alice, your code is: System.out.println()");
    }

    @Test
    void rendersTemplateWithMissingParamAsEmpty() {
        SkillDefinition def = new SkillDefinition();
        def.setName("test");
        def.setParameters(Map.of("type", "object"));

        SkillToolCallback callback = new SkillToolCallback(def,
                "Hello {{name}}", objectMapper);

        String result = callback.call("{}");
        assertThat(result).isEqualTo("Hello ");
    }

    @Test
    void handlesInvalidJsonGracefully() {
        SkillDefinition def = new SkillDefinition();
        def.setName("test");
        def.setParameters(Map.of("type", "object"));

        SkillToolCallback callback = new SkillToolCallback(def, "template", objectMapper);

        String result = callback.call("not valid json");
        assertThat(result).startsWith("Skill 'test' failed");
    }
}
