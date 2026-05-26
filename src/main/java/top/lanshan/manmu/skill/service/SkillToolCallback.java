package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.HashMap;
import java.util.Map;

public class SkillToolCallback implements ToolCallback {

    private static final Logger logger = LoggerFactory.getLogger(SkillToolCallback.class);

    private final SkillDefinition definition;
    private final String promptTemplate;
    private final ObjectMapper objectMapper;

    public SkillToolCallback(SkillDefinition definition, String promptTemplate, ObjectMapper objectMapper) {
        this.definition = definition;
        this.promptTemplate = promptTemplate;
        this.objectMapper = objectMapper;
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
        try {
            Map<String, Object> params = objectMapper.readValue(toolInput,
                    new TypeReference<HashMap<String, Object>>() {});
            return renderTemplate(params);
        } catch (Exception e) {
            String errorMsg = "Skill '" + definition.getName() + "' failed to process input: " + e.getMessage();
            logger.error(errorMsg);
            return errorMsg;
        }
    }

    private String renderTemplate(Map<String, Object> params) {
        return SkillService.renderTemplate(promptTemplate, params);
    }
}
