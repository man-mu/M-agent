package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import top.lanshan.manmu.skill.health.SkillInvocationHistoryService;

import java.util.HashMap;
import java.util.Map;

public class SkillToolCallback implements ToolCallback {

    private static final Logger logger = LoggerFactory.getLogger(SkillToolCallback.class);

    private final SkillDefinition definition;
    private final String promptTemplate;
    private final ObjectMapper objectMapper;
    private final SkillInvocationHistoryService invocationHistoryService;

    public SkillToolCallback(SkillDefinition definition, String promptTemplate, ObjectMapper objectMapper) {
        this(definition, promptTemplate, objectMapper, null);
    }

    public SkillToolCallback(SkillDefinition definition, String promptTemplate, ObjectMapper objectMapper,
            SkillInvocationHistoryService invocationHistoryService) {
        this.definition = definition;
        this.promptTemplate = promptTemplate;
        this.objectMapper = objectMapper;
        this.invocationHistoryService = invocationHistoryService;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        String toolName = "skill__" + definition.getName().replace('-', '_');
        return DefaultToolDefinition.builder()
                .name(toolName)
                .description(definition.getDescription())
                .inputSchema(definition.getInputSchemaJson())
                .build();
    }

    @Override
    public String call(String toolInput) {
        long started = System.nanoTime();
        Map<String, Object> params = Map.of();
        try {
            params = objectMapper.readValue(toolInput,
                    new TypeReference<HashMap<String, Object>>() {});
            String output = renderTemplate(params);
            record(params, output, "", started);
            return output;
        } catch (Exception e) {
            String errorMsg = "Skill '" + definition.getName() + "' failed to process input: " + safeMessage(e);
            logger.error(errorMsg);
            record(params, "", errorMsg, started);
            return errorMsg;
        }
    }

    private String renderTemplate(Map<String, Object> params) {
        return SkillService.renderTemplate(promptTemplate, params);
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
