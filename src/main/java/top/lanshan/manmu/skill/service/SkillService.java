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
import top.lanshan.manmu.skill.plugin.SkillPluginRegistry;

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
    private final SkillPluginRegistry pluginRegistry;
    private final boolean jarPluginsEnabled;

    public SkillService(SkillFileRepository fileRepository, SkillRegistry registry, ObjectMapper objectMapper) {
        this(fileRepository, registry, objectMapper, new SkillPackageArchiveService(objectMapper), null, null, false);
    }

    public SkillService(SkillFileRepository fileRepository, SkillRegistry registry, ObjectMapper objectMapper,
            SkillPackageArchiveService archiveService, SkillCatalogRepository catalogRepository) {
        this(fileRepository, registry, objectMapper, archiveService, catalogRepository, null, false);
    }

    public SkillService(SkillFileRepository fileRepository, SkillRegistry registry, ObjectMapper objectMapper,
            SkillPackageArchiveService archiveService, SkillCatalogRepository catalogRepository,
            SkillPluginRegistry pluginRegistry, boolean jarPluginsEnabled) {
        this.fileRepository = fileRepository;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.archiveService = archiveService;
        this.catalogRepository = catalogRepository;
        this.pluginRegistry = pluginRegistry;
        this.jarPluginsEnabled = jarPluginsEnabled;
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
        requirePromptDefinition(definition);
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
        requirePromptDefinition(definition);
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
            unregisterPlugin(name);
        }
        unregisterPlugin(name);
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
        unregisterPlugin(name);
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
        unregisterPlugin(name);
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

    public SkillPackageImportResult importJarPackage(String filename, byte[] zipBytes) throws IOException {
        requireJarPluginsEnabled();
        SkillPackageArchiveService.JarSkillPackage packageContent =
                archiveService.readJarPackage(filename, zipBytes);
        SkillDefinition definition = packageContent.definition();
        String name = definition.getName();
        if (fileRepository.exists(name)) {
            if (fileRepository.isBuiltin(name)) {
                throw new IllegalArgumentException("Built-in Skill '" + name + "' is read-only");
            }
            throw new IllegalArgumentException("Skill '" + name + "' already exists");
        }

        Instant now = Instant.now();
        if (definition.getInstalledAt() == null) {
            definition.setInstalledAt(now);
        }
        definition.setUpdatedAt(now);
        definition.setPackageType(SkillPackageType.JAR);
        definition.setSource("local-jar-package");

        SkillDefinition installedDefinition;
        try {
            fileRepository.writeJarSkill(name, definition, packageContent.pluginJar(), packageContent.readme());
            installedDefinition = fileRepository.readLocalDefinition(name)
                    .orElse(definition);
            if (installedDefinition.isEnabled()) {
                registerPlugin(installedDefinition);
            } else {
                unregisterPlugin(name);
            }
            registry.register(installedDefinition);
        } catch (RuntimeException | IOException e) {
            unregisterPlugin(name);
            try {
                fileRepository.deleteSkill(name);
            } catch (IOException cleanup) {
                logger.warn("Failed to cleanup rejected Jar Skill '{}': {}", name, safeMessage(cleanup));
            }
            throw e;
        }
        upsertCatalog(installedDefinition);
        logger.info("Jar Skill package imported: {}", name);
        return new SkillPackageImportResult(name, installedDefinition.getVersion(),
                installedDefinition.getPackageType(), installedDefinition.getStorageLocation(),
                installedDefinition.isEnabled(), "Jar Skill package imported");
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
        if (isJarSkill(definition)) {
            requireJarPluginsEnabled();
            registry.register(definition);
            if (definition.isEnabled()) {
                registerPlugin(definition);
            } else {
                unregisterPlugin(name);
            }
            logger.info("Jar Skill reloaded: {}", name);
            return definition;
        }
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
        unregisterPlugin(name);
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
        if (isJarSkill(def)) {
            requireJarPluginsEnabled();
            fileRepository.writeDefinition(name, def);
            registry.register(def);
            if (def.isEnabled()) {
                registerPlugin(def);
            } else {
                unregisterPlugin(name);
            }
        } else {
            fileRepository.writeSkill(name, def, registry.getPromptTemplate(name).orElse(""));
            registry.register(def, registry.getPromptTemplate(name).orElse(""));
        }
        upsertCatalog(def);
        logger.info("Skill '{}' toggled to enabled={}", name, def.isEnabled());
        return def;
    }

    private void requirePromptDefinition(SkillDefinition definition) {
        if (definition.getPackageType() != null && definition.getPackageType() != SkillPackageType.PROMPT) {
            throw new IllegalArgumentException("Use Jar Skill package import for Jar Skill definitions");
        }
        definition.setPackageType(SkillPackageType.PROMPT);
    }

    private void requireJarPluginsEnabled() {
        if (!jarPluginsEnabled) {
            throw new JarPluginsDisabledException("Jar Skill plugins are disabled");
        }
        if (pluginRegistry == null) {
            throw new JarPluginsDisabledException("Jar Skill plugin registry is not configured");
        }
    }

    private void registerPlugin(SkillDefinition definition) {
        requireJarPluginsEnabled();
        try {
            pluginRegistry.register(definition, fileRepository.packageDirectory(definition.getName()));
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Failed to load Jar Skill plugin: " + safeMessage(e), e);
        }
    }

    private void unregisterPlugin(String name) {
        if (pluginRegistry != null) {
            pluginRegistry.unregister(name);
        }
    }

    private boolean isJarSkill(SkillDefinition definition) {
        return definition.getPackageType() == SkillPackageType.JAR;
    }

    private String safeMessage(Throwable e) {
        if (e == null) {
            return "Unknown error";
        }
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
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

    public static class JarPluginsDisabledException extends RuntimeException {
        public JarPluginsDisabledException(String message) {
            super(message);
        }
    }
}
