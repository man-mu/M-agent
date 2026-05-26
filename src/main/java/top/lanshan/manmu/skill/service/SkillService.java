package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkillService {

    private static final Logger logger = LoggerFactory.getLogger(SkillService.class);

    private final SkillFileRepository fileRepository;
    private final SkillRegistry registry;
    private final ObjectMapper objectMapper;

    public SkillService(SkillFileRepository fileRepository, SkillRegistry registry, ObjectMapper objectMapper) {
        this.fileRepository = fileRepository;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public List<SkillDefinition> listAll() {
        return registry.listAll();
    }

    public Optional<SkillDefinition> getDefinition(String name) {
        return registry.getDefinition(name);
    }

    public String getPromptContent(String name) {
        return registry.getPromptTemplate(name).orElse(null);
    }

    public SkillDefinition create(SkillDefinition definition, String promptTemplate) throws IOException {
        if (fileRepository.exists(definition.getName())) {
            throw new IllegalArgumentException("Skill '" + definition.getName() + "' already exists");
        }
        fileRepository.writeSkill(definition.getName(), definition, promptTemplate);
        registry.register(definition, promptTemplate);
        logger.info("Skill created: {}", definition.getName());
        return definition;
    }

    public SkillDefinition update(String name, SkillDefinition definition, String promptTemplate) throws IOException {
        if (!fileRepository.exists(name)) {
            throw new IllegalArgumentException("Skill '" + name + "' not found");
        }
        if (!name.equals(definition.getName())) {
            fileRepository.deleteSkill(name);
            registry.unregister(name);
        }
        fileRepository.writeSkill(definition.getName(), definition, promptTemplate);
        registry.register(definition, promptTemplate);
        logger.info("Skill updated: {} -> {}", name, definition.getName());
        return definition;
    }

    public void delete(String name) throws IOException {
        if (!fileRepository.exists(name)) {
            throw new IllegalArgumentException("Skill '" + name + "' not found");
        }
        fileRepository.deleteSkill(name);
        registry.unregister(name);
        logger.info("Skill deleted: {}", name);
    }

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    /**
     * Renders a skill template with the given parameters for explicit
     * {@code @skill-name} invocation. Returns {@code null} if the skill is not
     * found or has no prompt template.
     */
    public String renderSkill(String name, Map<String, Object> params) {
        String template = registry.getPromptTemplate(name).orElse(null);
        if (template == null || template.isBlank()) {
            return null;
        }
        return renderTemplate(template, params);
    }

    /**
     * Shared template rendering: replaces {@code {{param}}} placeholders with
     * values from the provided map. Missing params are replaced with empty
     * strings. Used by both {@link SkillToolCallback} (auto-trigger via LLM
     * tool call) and {@code SpringAiAgentClient} (explicit {@code @skill}
     * invocation).
     */
    public static String renderTemplate(String template, Map<String, Object> params) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = params.getOrDefault(key, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public SkillDefinition toggle(String name) throws IOException {
        SkillDefinition def = registry.getDefinition(name)
                .orElseThrow(() -> new IllegalArgumentException("Skill '" + name + "' not found"));
        def.setEnabled(!def.isEnabled());
        fileRepository.writeSkill(name, def, registry.getPromptTemplate(name).orElse(""));
        registry.register(def, registry.getPromptTemplate(name).orElse(""));
        logger.info("Skill '{}' toggled to enabled={}", name, def.isEnabled());
        return def;
    }
}
