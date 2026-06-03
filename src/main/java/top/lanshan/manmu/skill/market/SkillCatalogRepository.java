package top.lanshan.manmu.skill.market;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SkillCatalogRepository {

    private static final Logger logger = LoggerFactory.getLogger(SkillCatalogRepository.class);
    private static final TypeReference<List<SkillCatalogEntry>> LIST_TYPE = new TypeReference<>() {};

    private final Path catalogPath;
    private final ObjectMapper objectMapper;

    public SkillCatalogRepository(Path localMarketPath, ObjectMapper objectMapper) {
        this.catalogPath = localMarketPath.resolve("catalog.json");
        this.objectMapper = objectMapper;
    }

    public synchronized List<SkillCatalogEntry> load() {
        ensureCatalogFile();
        try {
            List<SkillCatalogEntry> entries = objectMapper.readValue(catalogPath.toFile(), LIST_TYPE);
            return entries == null ? List.of() : entries;
        } catch (IOException e) {
            logger.warn("Failed to read Skill catalog {}, using empty catalog ({})",
                    catalogPath, e.getClass().getSimpleName());
            return List.of();
        }
    }

    public synchronized void save(List<SkillCatalogEntry> entries) {
        try {
            Files.createDirectories(catalogPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(catalogPath.toFile(), entries == null ? List.of() : entries);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write Skill catalog " + catalogPath, e);
        }
    }

    public synchronized void upsert(SkillCatalogEntry entry) {
        SkillPackageValidator.requireValidSkillName(entry.getName());
        List<SkillCatalogEntry> entries = new ArrayList<>(load());
        entries.removeIf(existing -> entry.getName().equals(existing.getName()));
        if (entry.getInstalledAt() == null) {
            entry.setInstalledAt(Instant.now());
        }
        entry.setUpdatedAt(Instant.now());
        entries.add(entry);
        save(entries);
    }

    public synchronized void remove(String name) {
        SkillPackageValidator.requireValidSkillName(name);
        List<SkillCatalogEntry> entries = new ArrayList<>(load());
        entries.removeIf(entry -> name.equals(entry.getName()));
        save(entries);
    }

    public Path catalogPath() {
        return catalogPath;
    }

    private void ensureCatalogFile() {
        if (Files.exists(catalogPath)) {
            return;
        }
        try {
            Files.createDirectories(catalogPath.getParent());
            Files.writeString(catalogPath, "[]");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize Skill catalog " + catalogPath, e);
        }
    }
}
