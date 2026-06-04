package top.lanshan.manmu.skill.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.lanshan.manmu.skill.service.SkillDefinition;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SkillPackageArchiveService {

    public static final long MAX_PACKAGE_BYTES = 2L * 1024L * 1024L;

    private static final String SKILL_JSON = "skill.json";
    private static final String SKILL_MD = "SKILL.md";
    private static final String README_MD = "README.md";

    private final ObjectMapper objectMapper;

    public SkillPackageArchiveService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PromptSkillPackage readPromptPackage(String filename, byte[] zipBytes) throws IOException {
        validatePackageInput(filename, zipBytes);
        byte[] skillJson = null;
        byte[] prompt = null;
        long extractedBytes = 0;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String entryName = normalizeEntryName(entry.getName());
                requireAllowedEntry(entryName);
                byte[] content = readEntryBytes(zip, MAX_PACKAGE_BYTES - extractedBytes);
                extractedBytes += content.length;
                if (SKILL_JSON.equals(entryName)) {
                    skillJson = content;
                } else if (SKILL_MD.equals(entryName)) {
                    prompt = content;
                }
                zip.closeEntry();
            }
        }

        if (skillJson == null) {
            throw new IllegalArgumentException("Skill package must contain skill.json");
        }
        if (prompt == null) {
            throw new IllegalArgumentException("Skill package must contain SKILL.md");
        }

        SkillDefinition definition = objectMapper.readValue(skillJson, SkillDefinition.class);
        SkillPackageValidator.requireValidDefinition(definition);
        if (definition.getPackageType() != null && definition.getPackageType() != SkillPackageType.PROMPT) {
            throw new IllegalArgumentException("Only prompt Skill zip packages are supported");
        }
        definition.setPackageType(SkillPackageType.PROMPT);
        return new PromptSkillPackage(definition, new String(prompt, StandardCharsets.UTF_8));
    }

    public byte[] writePromptPackage(SkillDefinition definition, String promptTemplate) throws IOException {
        SkillPackageValidator.requireValidDefinition(definition);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(SKILL_JSON));
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(definition));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry(SKILL_MD));
            zip.write((promptTemplate == null ? "" : promptTemplate).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        }
    }

    private void validatePackageInput(String filename, byte[] zipBytes) {
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("Skill package must be a .zip file");
        }
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("Skill package must not be empty");
        }
        if (zipBytes.length > MAX_PACKAGE_BYTES) {
            throw new IllegalArgumentException("Skill package is too large");
        }
    }

    private String normalizeEntryName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Skill package contains an empty entry name");
        }
        if (rawName.contains("\\") || rawName.startsWith("/") || rawName.startsWith("\\")
                || rawName.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Skill package contains an unsafe path");
        }
        String[] parts = rawName.split("/");
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException("Skill package contains an unsafe path");
            }
        }
        return String.join("/", parts);
    }

    private void requireAllowedEntry(String entryName) {
        String lower = entryName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jar") || lower.endsWith(".class") || lower.endsWith(".exe")
                || lower.endsWith(".dll") || lower.endsWith(".bat") || lower.endsWith(".cmd")
                || lower.endsWith(".ps1") || lower.endsWith(".sh") || lower.endsWith(".js")
                || lower.endsWith(".ts") || lower.endsWith(".py")) {
            throw new IllegalArgumentException("Skill package contains executable content");
        }
        if (SKILL_JSON.equals(entryName) || SKILL_MD.equals(entryName) || README_MD.equals(entryName)) {
            return;
        }
        if (entryName.startsWith("assets/") && !entryName.endsWith("/")) {
            return;
        }
        throw new IllegalArgumentException("Skill package contains unsupported file: " + entryName);
    }

    private byte[] readEntryBytes(ZipInputStream zip, long remainingLimit) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = zip.read(buffer)) != -1) {
                total += read;
                if (total > remainingLimit) {
                    throw new IllegalArgumentException("Skill package is too large");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    public record PromptSkillPackage(SkillDefinition definition, String promptTemplate) {
    }
}
