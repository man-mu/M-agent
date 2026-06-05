package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import top.lanshan.manmu.skill.health.SkillInvocationHistoryService;
import top.lanshan.manmu.skill.market.SkillPackageType;
import top.lanshan.manmu.skill.plugin.JarSkillToolCallback;
import top.lanshan.manmu.skill.plugin.SkillPluginRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SkillToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(SkillToolProvider.class);

    private final SkillRegistry registry;
    private final SkillFileRepository fileRepository;
    private final ObjectMapper objectMapper;
    private final SkillPluginRegistry pluginRegistry;
    private final boolean jarPluginsEnabled;
    private final SkillInvocationHistoryService invocationHistoryService;

    public SkillToolProvider(SkillRegistry registry, ObjectMapper objectMapper) {
        this(registry, null, objectMapper, null, false, null);
    }

    public SkillToolProvider(SkillRegistry registry, SkillFileRepository fileRepository,
            ObjectMapper objectMapper, SkillPluginRegistry pluginRegistry, boolean jarPluginsEnabled) {
        this(registry, fileRepository, objectMapper, pluginRegistry, jarPluginsEnabled, null);
    }

    public SkillToolProvider(SkillRegistry registry, SkillFileRepository fileRepository,
            ObjectMapper objectMapper, SkillPluginRegistry pluginRegistry, boolean jarPluginsEnabled,
            SkillInvocationHistoryService invocationHistoryService) {
        this.registry = registry;
        this.fileRepository = fileRepository;
        this.objectMapper = objectMapper;
        this.pluginRegistry = pluginRegistry;
        this.jarPluginsEnabled = jarPluginsEnabled;
        this.invocationHistoryService = invocationHistoryService;
    }

    public ToolCallback[] getToolCallbacks() {
        List<SkillDefinition> enabled = registry.listEnabled();
        if (enabled.isEmpty()) {
            return new ToolCallback[0];
        }

        List<ToolCallback> callbacks = new ArrayList<>();
        for (SkillDefinition def : enabled) {
            if (def.getPackageType() == SkillPackageType.JAR) {
                addJarSkillCallback(callbacks, def);
                continue;
            }
            String template = registry.getPromptTemplate(def.getName()).orElse(null);
            if (template == null || template.isBlank()) {
                logger.warn("Skill '{}' has no prompt template, skipping", def.getName());
                continue;
            }
            callbacks.add(new SkillToolCallback(def, template, objectMapper, invocationHistoryService));
        }
        logger.info("Skill tools ready: {} tools", callbacks.size());
        return callbacks.toArray(new ToolCallback[0]);
    }

    private void addJarSkillCallback(List<ToolCallback> callbacks, SkillDefinition def) {
        if (!jarPluginsEnabled || pluginRegistry == null || fileRepository == null) {
            logger.warn("Jar Skill '{}' skipped because Jar plugins are disabled", def.getName());
            return;
        }
        try {
            if (!pluginRegistry.hasPlugin(def.getName())) {
                pluginRegistry.register(def, fileRepository.packageDirectory(def.getName()));
            }
            callbacks.add(new JarSkillToolCallback(def, pluginRegistry, objectMapper, invocationHistoryService));
        } catch (IOException | RuntimeException e) {
            logger.warn("Jar Skill '{}' failed to load: {}", def.getName(), safeMessage(e));
        }
    }

    /**
     * Returns a human-readable summary of enabled skills for injection into the
     * LLM system prompt. This improves auto-trigger discoverability — the LLM
     * sees both the tool definitions (via ToolCallback) and this contextual
     * prompt telling it <em>when</em> to use each skill.
     *
     * @return a prompt fragment, or empty string if no skills are enabled
     */
    public String getSkillSummary() {
        List<SkillDefinition> enabled = registry.listEnabled();
        if (enabled.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Available Skills\n\n");
        sb.append("You have access to the following skill tools. ")
          .append("Use the corresponding tool when a user request matches.\n\n");
        for (SkillDefinition def : enabled) {
            String toolName = "skill__" + def.getName().replace('-', '_');
            sb.append("- **").append(toolName).append("**: ")
              .append(def.getDescription()).append("\n");
        }
        sb.append("\n");
        return sb.toString();
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
