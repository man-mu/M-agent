package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class SkillFileRepository {

    private static final Logger logger = LoggerFactory.getLogger(SkillFileRepository.class);
    private static final String SKILL_JSON = "skill.json";
    private static final String SKILL_MD = "SKILL.md";

    private final Path contentBasePath;
    private final ObjectMapper objectMapper;

    public SkillFileRepository(Path contentBasePath, ObjectMapper objectMapper) {
        this.contentBasePath = contentBasePath;
        this.objectMapper = objectMapper;
    }

    public List<String> listSkillNames() {
        List<String> names = new ArrayList<>();
        if (!Files.exists(contentBasePath)) {
            return names;
        }
        try (Stream<Path> dirs = Files.list(contentBasePath)) {
            dirs.filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .forEach(names::add);
        } catch (IOException e) {
            logger.error("Failed to list skill directories: {}", e.getMessage());
        }
        return names;
    }

    public Optional<SkillDefinition> readDefinition(String name) {
        Path jsonFile = contentBasePath.resolve(name).resolve(SKILL_JSON);
        if (!Files.exists(jsonFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(jsonFile.toFile(), SkillDefinition.class));
        } catch (IOException e) {
            logger.error("Failed to read skill.json for '{}': {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> readPromptTemplate(String name) {
        Path mdFile = contentBasePath.resolve(name).resolve(SKILL_MD);
        if (!Files.exists(mdFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(mdFile));
        } catch (IOException e) {
            logger.error("Failed to read SKILL.md for '{}': {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    public void writeSkill(String name, SkillDefinition definition, String promptTemplate) throws IOException {
        Path skillDir = contentBasePath.resolve(name);
        Files.createDirectories(skillDir);

        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(skillDir.resolve(SKILL_JSON).toFile(), definition);

        Files.writeString(skillDir.resolve(SKILL_MD), promptTemplate == null ? "" : promptTemplate);
        logger.info("Skill '{}' written to {}", name, skillDir);
    }

    public void deleteSkill(String name) throws IOException {
        Path skillDir = contentBasePath.resolve(name);
        if (!Files.exists(skillDir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(skillDir)) {
            files.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
        }
        logger.info("Skill '{}' deleted from {}", name, skillDir);
    }

    public boolean exists(String name) {
        return Files.exists(contentBasePath.resolve(name));
    }
}
