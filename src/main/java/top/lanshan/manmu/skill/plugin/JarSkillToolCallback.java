package top.lanshan.manmu.skill.plugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import top.lanshan.manmu.skill.service.SkillDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

public class JarSkillToolCallback implements ToolCallback {

    private static final Logger logger = LoggerFactory.getLogger(JarSkillToolCallback.class);

    private final SkillDefinition definition;
    private final SkillPluginRegistry pluginRegistry;
    private final ObjectMapper objectMapper;

    public JarSkillToolCallback(SkillDefinition definition, SkillPluginRegistry pluginRegistry,
            ObjectMapper objectMapper) {
        this.definition = definition;
        this.pluginRegistry = pluginRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return DefaultToolDefinition.builder()
                .name("skill__" + definition.getName().replace('-', '_'))
                .description(definition.getDescription())
                .inputSchema(definition.getInputSchemaJson())
                .build();
    }

    @Override
    public String call(String toolInput) {
        try {
            Map<String, Object> params = objectMapper.readValue(toolInput,
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            return pluginRegistry.invoke(definition.getName(), params);
        } catch (Exception e) {
            String message = "Jar Skill '" + definition.getName() + "' failed: " + safeMessage(e);
            logger.warn(message);
            return message;
        }
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
