package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.lanshan.manmu.skill.market.SkillCatalogEntry;
import top.lanshan.manmu.skill.market.SkillCatalogRepository;
import top.lanshan.manmu.skill.market.SkillPackageArchiveService;
import top.lanshan.manmu.skill.market.SkillPackageImportResult;
import top.lanshan.manmu.skill.market.SkillPackageStatus;
import top.lanshan.manmu.skill.market.SkillPackageType;
import top.lanshan.manmu.skill.market.SkillPackageValidator;

import java.io.IOException;
import java.time.Instant;
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
    private final SkillPackageArchiveService archiveService;
    private final SkillCatalogRepository catalogRepository;

    public SkillService(SkillFileRepository fileRepository, SkillRegistry registry, ObjectMapper objectMapper) {
        this(fileRepository, registry, objectMapper, new SkillPackageArchiveService(objectMapper), null);
    }

    public SkillService(SkillFileRepository fileRepository, SkillRegistry registry, ObjectMapper objectMapper,
            SkillPackageArchiveService archiveService, SkillCatalogRepository catalogRepository) {
        this.fileRepository = fileRepository;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.archiveService = archiveService;
        this.catalogRepository = catalogRepository;
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
        SkillPackageValidator.requireValidDefinition(definition);
        if (fileRepository.exists(definition.getName())) {
            throw new IllegalArgumentException("Skill '" + definition.getName() + "' already exists");
        }
        fileRepository.writeSkill(definition.getName(), definition, promptTemplate);
        registry.register(definition, promptTemplate);
        logger.info("Skill created: {}", definition.getName());
        return definition;
    }

    public SkillDefinition update(String name, SkillDefinition definition, String promptTemplate) throws IOException {
        SkillPackageValidator.requireValidSkillName(name);
        SkillPackageValidator.requireValidDefinition(definition);
        if (!fileRepository.exists(name)) {
            throw new IllegalArgumentException("Skill '" + name + "' not found");
        }
        if (fileRepository.isBuiltin(name)) {
            throw new IllegalArgumentException("Built-in Skill '" + name + "' is read-only");
        }
        if (!name.equals(definition.getName()) && fileRepository.exists(definition.getName())) {
            throw new IllegalArgumentException("Skill '" + definition.getName() + "' already exists");
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
        SkillPackageValidator.requireValidSkillName(name);
        if (!fileRepository.exists(name)) {
            throw new IllegalArgumentException("Skill '" + name + "' not found");
        }
        if (fileRepository.isBuiltin(name)) {
            throw new IllegalArgumentException("Built-in Skill '" + name + "' is read-only");
        }
        fileRepository.deleteSkill(name);
        registry.unregister(name);
        logger.info("Skill deleted: {}", name);
    }

    public SkillPackageImportResult importPromptPackage(String filename, byte[] zipBytes) throws IOException {
        SkillPackageArchiveService.PromptSkillPackage packageContent =
                archiveService.readPromptPackage(filename, zipBytes);
        SkillDefinition definition = packageContent.definition();
        String name = definition.getName();
        if (fileRepository.exists(name) && fileRepository.isBuiltin(name)) {
            throw new IllegalArgumentException("Built-in Skill '" + name + "' is read-only");
        }
        Instant now = Instant.now();
        if (definition.getInstalledAt() == null) {
            definition.setInstalledAt(now);
        }
        definition.setUpdatedAt(now);
        definition.setPackageType(SkillPackageType.PROMPT);
        definition.setSource("local-package");

        fileRepository.writeSkill(name, definition, packageContent.promptTemplate());
        SkillDefinition installedDefinition = fileRepository.readLocalDefinition(name)
                .orElse(definition);
        String promptTemplate = fileRepository.readLocalPromptTemplate(name)
                .orElse(packageContent.promptTemplate());
        registry.register(installedDefinition, promptTemplate);
        upsertCatalog(installedDefinition);
        logger.info("Prompt Skill package imported: {}", name);
        return new SkillPackageImportResult(name, installedDefinition.getVersion(),
                installedDefinition.getPackageType(), installedDefinition.getStorageLocation(),
                installedDefinition.isEnabled(), "Skill package imported");
    }

    public byte[] exportPromptPackage(String name) throws IOException {
        SkillPackageValidator.requireValidSkillName(name);
        SkillDefinition definition = registry.getDefinition(name)
                .orElseThrow(() -> new IllegalArgumentException("Skill '" + name + "' not found"));
        if (definition.getPackageType() != null && definition.getPackageType() != SkillPackageType.PROMPT) {
            throw new IllegalArgumentException("Only prompt Skill packages can be exported");
        }
        String promptTemplate = registry.getPromptTemplate(name).orElse("");
        return archiveService.writePromptPackage(definition, promptTemplate);
    }

    public SkillDefinition reload(String name) {
        SkillPackageValidator.requireValidSkillName(name);
        SkillDefinition definition = fileRepository.readDefinition(name)
                .orElseThrow(() -> new IllegalArgumentException("Skill '" + name + "' not found"));
        String promptTemplate = fileRepository.readPromptTemplate(name)
                .orElseThrow(() -> new IllegalArgumentException("Skill '" + name + "' missing SKILL.md"));
        registry.register(definition, promptTemplate);
        logger.info("Skill reloaded: {}", name);
        return definition;
    }

    public void uninstallPackage(String name) throws IOException {
        SkillPackageValidator.requireValidSkillName(name);
        if (!fileRepository.exists(name)) {
            throw new IllegalArgumentException("Skill '" + name + "' not found");
        }
        if (fileRepository.isBuiltin(name)) {
            throw new IllegalArgumentException("Built-in Skill '" + name + "' is read-only");
        }
        fileRepository.deleteSkill(name);
        registry.unregister(name);
        if (catalogRepository != null) {
            catalogRepository.remove(name);
        }
        logger.info("Skill package uninstalled: {}", name);
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
        SkillPackageValidator.requireValidSkillName(name);
        SkillDefinition def = registry.getDefinition(name)
                .orElseThrow(() -> new IllegalArgumentException("Skill '" + name + "' not found"));
        if (fileRepository.isBuiltin(name)) {
            throw new IllegalArgumentException("Built-in Skill '" + name + "' is read-only");
        }
        def.setEnabled(!def.isEnabled());
        fileRepository.writeSkill(name, def, registry.getPromptTemplate(name).orElse(""));
        registry.register(def, registry.getPromptTemplate(name).orElse(""));
        logger.info("Skill '{}' toggled to enabled={}", name, def.isEnabled());
        return def;
    }

    private void upsertCatalog(SkillDefinition definition) {
        if (catalogRepository == null) {
            return;
        }
        SkillCatalogEntry entry = new SkillCatalogEntry();
        entry.setName(definition.getName());
        entry.setVersion(definition.getVersion());
        entry.setPackageType(definition.getPackageType());
        entry.setStatus(definition.isEnabled() ? SkillPackageStatus.ENABLED : SkillPackageStatus.DISABLED);
        entry.setSource(definition.getSource());
        entry.setInstalledAt(definition.getInstalledAt());
        entry.setUpdatedAt(definition.getUpdatedAt());
        catalogRepository.upsert(entry);
    }
}
