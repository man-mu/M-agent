package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.lanshan.manmu.skill.market.SkillPackageType;
import top.lanshan.manmu.skill.market.SkillPackageValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class SkillFileRepository {

    private static final Logger logger = LoggerFactory.getLogger(SkillFileRepository.class);
    private static final String SKILL_JSON = "skill.json";
    private static final String SKILL_MD = "SKILL.md";
    private static final String PLUGIN_JAR = "plugin.jar";

    private final List<SkillRoot> roots;
    private final ObjectMapper objectMapper;

    public SkillFileRepository(Path contentBasePath, ObjectMapper objectMapper) {
        this(List.of(new SkillRoot(contentBasePath, SkillStorageLocation.LOCAL, true, "local")),
                objectMapper);
    }

    public SkillFileRepository(Path builtinContentPath, Path localInstalledPath, ObjectMapper objectMapper) {
        this(List.of(
                new SkillRoot(builtinContentPath, SkillStorageLocation.BUILTIN, false, "builtin"),
                new SkillRoot(localInstalledPath, SkillStorageLocation.LOCAL, true, "local")),
                objectMapper);
    }

    private SkillFileRepository(List<SkillRoot> roots, ObjectMapper objectMapper) {
        this.roots = roots.stream()
                .map(root -> new SkillRoot(root.path().toAbsolutePath().normalize(),
                        root.location(), root.writable(), root.source()))
                .toList();
        this.objectMapper = objectMapper;
    }

    public List<String> listSkillNames() {
        Set<String> names = new LinkedHashSet<>();
        for (SkillRoot root : roots) {
            if (!Files.exists(root.path())) {
                continue;
            }
            try (Stream<Path> dirs = Files.list(root.path())) {
                dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(this::validListedName)
                    .forEach(names::add);
            } catch (IOException e) {
                logger.error("Failed to list skill directories under {}: {}", root.path(), e.getMessage());
            }
        }
        return new ArrayList<>(names);
    }

    public Optional<SkillDefinition> readDefinition(String name) {
        validateName(name);
        return findRootContaining(name).flatMap(root -> {
            Path jsonFile = skillDir(root, name).resolve(SKILL_JSON);
            if (!Files.exists(jsonFile)) {
                return Optional.empty();
            }
            try {
                SkillDefinition definition = objectMapper.readValue(jsonFile.toFile(), SkillDefinition.class);
                enrichDefinition(definition, root);
                return Optional.of(definition);
            } catch (IOException e) {
                logger.error("Failed to read skill.json for '{}': {}", name, e.getMessage());
                return Optional.empty();
            }
        });
    }

    public Optional<String> readPromptTemplate(String name) {
        validateName(name);
        return findRootContaining(name).flatMap(root -> {
            Path mdFile = skillDir(root, name).resolve(SKILL_MD);
            if (!Files.exists(mdFile)) {
                return Optional.empty();
            }
            try {
                return Optional.of(Files.readString(mdFile));
            } catch (IOException e) {
                logger.error("Failed to read SKILL.md for '{}': {}", name, e.getMessage());
                return Optional.empty();
            }
        });
    }

    public Optional<SkillDefinition> readLocalDefinition(String name) {
        validateName(name);
        return findLocalRootContaining(name).flatMap(root -> {
            Path jsonFile = skillDir(root, name).resolve(SKILL_JSON);
            if (!Files.exists(jsonFile)) {
                return Optional.empty();
            }
            try {
                SkillDefinition definition = objectMapper.readValue(jsonFile.toFile(), SkillDefinition.class);
                enrichDefinition(definition, root);
                return Optional.of(definition);
            } catch (IOException e) {
                logger.error("Failed to read local skill.json for '{}': {}", name, e.getMessage());
                return Optional.empty();
            }
        });
    }

    public Optional<String> readLocalPromptTemplate(String name) {
        validateName(name);
        return findLocalRootContaining(name).flatMap(root -> {
            Path mdFile = skillDir(root, name).resolve(SKILL_MD);
            if (!Files.exists(mdFile)) {
                return Optional.empty();
            }
            try {
                return Optional.of(Files.readString(mdFile));
            } catch (IOException e) {
                logger.error("Failed to read local SKILL.md for '{}': {}", name, e.getMessage());
                return Optional.empty();
            }
        });
    }

    public void writeSkill(String name, SkillDefinition definition, String promptTemplate) throws IOException {
        validateName(name);
        SkillPackageValidator.requireValidDefinition(definition);
        if (!name.equals(definition.getName())) {
            throw new IllegalArgumentException("Skill path name must match definition name");
        }
        SkillRoot root = findRootContaining(name)
                .orElseGet(this::writableRoot);
        ensureWritable(root, name);
        Path skillDir = skillDir(root, name);
        Files.createDirectories(skillDir);

        enrichDefinitionForWrite(definition, root);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(skillDir.resolve(SKILL_JSON).toFile(), definition);

        Files.writeString(skillDir.resolve(SKILL_MD), promptTemplate == null ? "" : promptTemplate);
        logger.info("Skill '{}' written to {}", name, skillDir);
    }

    public void writeJarSkill(String name, SkillDefinition definition, byte[] pluginJar, String readme)
            throws IOException {
        validateName(name);
        SkillPackageValidator.requireValidDefinition(definition);
        if (!name.equals(definition.getName())) {
            throw new IllegalArgumentException("Skill path name must match definition name");
        }
        if (pluginJar == null || pluginJar.length == 0) {
            throw new IllegalArgumentException("Jar Skill package must contain plugin.jar");
        }
        SkillRoot root = findRootContaining(name)
                .orElseGet(this::writableRoot);
        ensureWritable(root, name);
        Path skillDir = skillDir(root, name);
        Files.createDirectories(skillDir);

        enrichDefinitionForWrite(definition, root);
        definition.setPackageType(SkillPackageType.JAR);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(skillDir.resolve(SKILL_JSON).toFile(), definition);
        Files.write(skillDir.resolve(PLUGIN_JAR), pluginJar);
        if (readme != null) {
            Files.writeString(skillDir.resolve("README.md"), readme);
        }
        Files.deleteIfExists(skillDir.resolve(SKILL_MD));
        logger.info("Jar Skill '{}' written to {}", name, skillDir);
    }

    public void writeDefinition(String name, SkillDefinition definition) throws IOException {
        validateName(name);
        SkillPackageValidator.requireValidDefinition(definition);
        if (!name.equals(definition.getName())) {
            throw new IllegalArgumentException("Skill path name must match definition name");
        }
        SkillRoot root = findRootContaining(name)
                .orElseThrow(() -> new IllegalArgumentException("Skill '" + name + "' not found"));
        ensureWritable(root, name);
        enrichDefinitionForWrite(definition, root);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(skillDir(root, name).resolve(SKILL_JSON).toFile(), definition);
    }

    public void deleteSkill(String name) throws IOException {
        validateName(name);
        Optional<SkillRoot> root = findRootContaining(name);
        if (root.isEmpty()) {
            return;
        }
        ensureWritable(root.get(), name);
        Path skillDir = skillDir(root.get(), name);
        if (!Files.exists(skillDir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(skillDir)) {
            files.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
        }
        logger.info("Skill '{}' deleted from {}", name, skillDir);
    }

    public boolean exists(String name) {
        validateName(name);
        return findRootContaining(name).isPresent();
    }

    public boolean isBuiltin(String name) {
        validateName(name);
        return findRootContaining(name)
                .map(root -> root.location() == SkillStorageLocation.BUILTIN)
                .orElse(false);
    }

    public SkillStorageLocation storageLocation(String name) {
        validateName(name);
        return findRootContaining(name)
                .map(SkillRoot::location)
                .orElse(null);
    }

    public Path localInstalledPath() {
        return writableRoot().path();
    }

    public Path localSkillPath(String name) {
        validateName(name);
        return skillDir(writableRoot(), name);
    }

    public Path packageDirectory(String name) {
        validateName(name);
        return findRootContaining(name)
                .map(root -> skillDir(root, name))
                .orElseThrow(() -> new IllegalArgumentException("Skill '" + name + "' not found"));
    }

    private boolean validListedName(String name) {
        boolean valid = SkillPackageValidator.isValidSkillName(name);
        if (!valid) {
            logger.warn("Skipping invalid Skill directory name: {}", name);
        }
        return valid;
    }

    private Optional<SkillRoot> findRootContaining(String name) {
        return roots.stream()
                .filter(root -> Files.isDirectory(skillDir(root, name)))
                .findFirst();
    }

    private Optional<SkillRoot> findLocalRootContaining(String name) {
        return roots.stream()
                .filter(SkillRoot::writable)
                .filter(root -> Files.isDirectory(skillDir(root, name)))
                .findFirst();
    }

    private SkillRoot writableRoot() {
        return roots.stream()
                .filter(SkillRoot::writable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No writable Skill root configured"));
    }

    private void validateName(String name) {
        SkillPackageValidator.requireValidSkillName(name);
    }

    private void ensureWritable(SkillRoot root, String name) {
        if (!root.writable()) {
            throw new IllegalArgumentException("Built-in Skill '" + name + "' is read-only");
        }
    }

    private Path skillDir(SkillRoot root, String name) {
        Path skillDir = root.path().resolve(name).normalize();
        if (!skillDir.startsWith(root.path())) {
            throw new IllegalArgumentException("Skill path escapes configured root");
        }
        return skillDir;
    }

    private void enrichDefinition(SkillDefinition definition, SkillRoot root) {
        if (definition.getPackageType() == null) {
            definition.setPackageType(SkillPackageType.PROMPT);
        }
        definition.setStorageLocation(root.location());
        if (definition.getSource() == null || definition.getSource().isBlank()) {
            definition.setSource(root.source());
        }
    }

    private void enrichDefinitionForWrite(SkillDefinition definition, SkillRoot root) {
        enrichDefinition(definition, root);
        definition.setUpdatedAt(java.time.Instant.now());
        if (definition.getInstalledAt() == null) {
            definition.setInstalledAt(definition.getCreatedAt());
        }
    }

    private record SkillRoot(Path path, SkillStorageLocation location, boolean writable, String source) {
    }
}
