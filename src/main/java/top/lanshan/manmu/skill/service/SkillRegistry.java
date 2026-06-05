package top.lanshan.manmu.skill.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import top.lanshan.manmu.skill.market.SkillPackageType;

public class SkillRegistry {

    private static final Logger logger = LoggerFactory.getLogger(SkillRegistry.class);

    private final SkillFileRepository fileRepository;
    private final Map<String, SkillDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, String> promptTemplates = new LinkedHashMap<>();

    public SkillRegistry(SkillFileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public void loadAll() {
        definitions.clear();
        promptTemplates.clear();
        for (String name : fileRepository.listSkillNames()) {
            fileRepository.readDefinition(name).ifPresentOrElse(
                def -> {
                    definitions.put(name, def);
                    if (isJarSkill(def)) {
                        promptTemplates.remove(name);
                    } else {
                        fileRepository.readPromptTemplate(name).ifPresentOrElse(
                            tmpl -> promptTemplates.put(name, tmpl),
                            () -> logger.warn("Skill '{}' missing SKILL.md", name));
                    }
                    logger.info("Skill loaded: {} (enabled={})", name, def.isEnabled());
                },
                () -> logger.warn("Skill directory '{}' missing skill.json, skipped", name));
        }
        logger.info("SkillRegistry loaded: {} skills", definitions.size());
    }

    public void reload() {
        loadAll();
    }

    public List<SkillDefinition> listAll() {
        return new ArrayList<>(definitions.values());
    }

    public List<SkillDefinition> listEnabled() {
        return definitions.values().stream()
                .filter(SkillDefinition::isEnabled)
                .toList();
    }

    public Optional<SkillDefinition> getDefinition(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    public Optional<String> getPromptTemplate(String name) {
        return Optional.ofNullable(promptTemplates.get(name));
    }

    public void register(SkillDefinition definition, String promptTemplate) {
        definitions.put(definition.getName(), definition);
        if (promptTemplate == null) {
            promptTemplates.remove(definition.getName());
        } else {
            promptTemplates.put(definition.getName(), promptTemplate);
        }
    }

    public void register(SkillDefinition definition) {
        definitions.put(definition.getName(), definition);
        promptTemplates.remove(definition.getName());
    }

    public void unregister(String name) {
        definitions.remove(name);
        promptTemplates.remove(name);
    }

    private boolean isJarSkill(SkillDefinition definition) {
        return definition.getPackageType() == SkillPackageType.JAR;
    }
}
