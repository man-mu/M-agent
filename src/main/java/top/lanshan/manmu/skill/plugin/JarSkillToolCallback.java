package top.lanshan.manmu.skill.plugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import top.lanshan.manmu.skill.health.SkillInvocationHistoryService;
import top.lanshan.manmu.skill.service.SkillDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

public class JarSkillToolCallback implements ToolCallback {

    private static final Logger logger = LoggerFactory.getLogger(JarSkillToolCallback.class);

    private final SkillDefinition definition;
    private final SkillPluginRegistry pluginRegistry;
    private final ObjectMapper objectMapper;
    private final SkillInvocationHistoryService invocationHistoryService;

    public JarSkillToolCallback(SkillDefinition definition, SkillPluginRegistry pluginRegistry,
            ObjectMapper objectMapper) {
        this(definition, pluginRegistry, objectMapper, null);
    }

    public JarSkillToolCallback(SkillDefinition definition, SkillPluginRegistry pluginRegistry,
            ObjectMapper objectMapper, SkillInvocationHistoryService invocationHistoryService) {
        this.definition = definition;
        this.pluginRegistry = pluginRegistry;
        this.objectMapper = objectMapper;
        this.invocationHistoryService = invocationHistoryService;
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
        if (!pluginRegistry.hasPlugin(definition.getName())) {
            String message = "Skill '" + definition.getName() + "' is currently unavailable (disabled or uninstalled)";
            logger.warn(message);
            return message;
        }
        long started = System.nanoTime();
        Map<String, Object> params = Map.of();
        try {
            params = objectMapper.readValue(toolInput,
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            String output = pluginRegistry.invoke(definition.getName(), params);
            record(params, output, "", started);
            return output;
        } catch (Exception e) {
            String message = "Jar Skill '" + definition.getName() + "' failed: " + safeMessage(e);
            logger.warn(message);
            record(params, "", message, started);
            return message;
        }
    }

    private void record(Map<String, Object> input, String output, String error, long started) {
        if (invocationHistoryService != null) {
            invocationHistoryService.record(definition.getName(), "TOOL", input, output, error,
                    invocationHistoryService.durationMs(started));
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
