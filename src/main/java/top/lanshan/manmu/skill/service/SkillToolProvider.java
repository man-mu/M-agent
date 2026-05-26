package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

public class SkillToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(SkillToolProvider.class);

    private final SkillRegistry registry;
    private final ObjectMapper objectMapper;

    public SkillToolProvider(SkillRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public ToolCallback[] getToolCallbacks() {
        List<SkillDefinition> enabled = registry.listEnabled();
        if (enabled.isEmpty()) {
            return new ToolCallback[0];
        }

        List<ToolCallback> callbacks = new ArrayList<>();
        for (SkillDefinition def : enabled) {
            String template = registry.getPromptTemplate(def.getName()).orElse(null);
            if (template == null || template.isBlank()) {
                logger.warn("Skill '{}' has no prompt template, skipping", def.getName());
                continue;
            }
            callbacks.add(new SkillToolCallback(def, template, objectMapper));
        }
        logger.info("Skill tools ready: {} tools", callbacks.size());
        return callbacks.toArray(new ToolCallback[0]);
    }
}
