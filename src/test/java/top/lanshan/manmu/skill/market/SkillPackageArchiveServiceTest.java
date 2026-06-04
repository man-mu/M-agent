package top.lanshan.manmu.skill.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.lanshan.manmu.skill.service.SkillDefinition;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillPackageArchiveServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SkillPackageArchiveService archiveService = new SkillPackageArchiveService(objectMapper);

    @Test
    void roundTripsPromptSkillPackage() throws Exception {
        SkillDefinition definition = definition("sample-skill");
        definition.setParameters(Map.of("type", "object"));

        byte[] zip = archiveService.writePromptPackage(definition, "Hello {{name}}");
        SkillPackageArchiveService.PromptSkillPackage imported =
                archiveService.readPromptPackage("sample-skill.zip", zip);

        assertThat(imported.definition().getName()).isEqualTo("sample-skill");
        assertThat(imported.definition().getPackageType()).isEqualTo(SkillPackageType.PROMPT);
        assertThat(imported.promptTemplate()).isEqualTo("Hello {{name}}");
    }

    @Test
    void rejectsZipSlipEntries() throws Exception {
        byte[] zip = zipWith(
                entry("../evil.txt", "bad"),
                entry("skill.json", objectMapper.writeValueAsString(definition("bad-skill"))),
                entry("SKILL.md", "prompt"));

        assertThatThrownBy(() -> archiveService.readPromptPackage("bad.zip", zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe path");
    }

    @Test
    void rejectsExecutableContent() throws Exception {
        byte[] zip = zipWith(
                entry("skill.json", objectMapper.writeValueAsString(definition("bad-skill"))),
                entry("SKILL.md", "prompt"),
                entry("lib/plugin.jar", "jar"));

        assertThatThrownBy(() -> archiveService.readPromptPackage("bad.zip", zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executable content");
    }

    @Test
    void rejectsUnsupportedRootFiles() throws Exception {
        byte[] zip = zipWith(
                entry("skill.json", objectMapper.writeValueAsString(definition("bad-skill"))),
                entry("SKILL.md", "prompt"),
                entry("notes.txt", "not allowed"));

        assertThatThrownBy(() -> archiveService.readPromptPackage("bad.zip", zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported file");
    }

    @Test
    void rejectsExpandedPackagesOverLimit() throws Exception {
        byte[] hugePrompt = new byte[(int) SkillPackageArchiveService.MAX_PACKAGE_BYTES + 1];
        Arrays.fill(hugePrompt, (byte) 'x');
        byte[] zip = zipWith(
                entry("skill.json", objectMapper.writeValueAsString(definition("huge-skill"))),
                entry("SKILL.md", hugePrompt));

        assertThatThrownBy(() -> archiveService.readPromptPackage("huge.zip", zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void rejectsNonZipFilenames() {
        assertThatThrownBy(() -> archiveService.readPromptPackage("skill.txt", new byte[] { 1, 2, 3 }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".zip");
    }

    private SkillDefinition definition(String name) {
        SkillDefinition definition = new SkillDefinition();
        definition.setName(name);
        definition.setDescription("A packaged Skill");
        definition.setVersion("1.0.0");
        return definition;
    }

    private ZipItem entry(String name, String content) {
        return entry(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private ZipItem entry(String name, byte[] content) {
        return new ZipItem(name, content);
    }

    private byte[] zipWith(ZipItem... entries) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (ZipItem item : entries) {
                zip.putNextEntry(new ZipEntry(item.name()));
                zip.write(item.content());
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        }
    }

    private record ZipItem(String name, byte[] content) {
    }
}
