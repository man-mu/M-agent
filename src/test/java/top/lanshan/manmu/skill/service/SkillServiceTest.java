package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void rejectsBuiltinMutationBeforeChangingRegistryState() throws Exception {
        Path builtin = tempDir.resolve("builtin");
        Path local = tempDir.resolve("local");
        SkillDefinition definition = new SkillDefinition();
        definition.setName("builtin-skill");
        definition.setEnabled(true);
        writeRawSkill(builtin.resolve("builtin-skill"), definition, "prompt");

        SkillFileRepository repository = new SkillFileRepository(builtin, local, objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        registry.loadAll();
        SkillService service = new SkillService(repository, registry, objectMapper);

        assertThatThrownBy(() -> service.toggle("builtin-skill"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read-only");
        assertThat(registry.getDefinition("builtin-skill").orElseThrow().isEnabled()).isTrue();

        SkillDefinition updated = new SkillDefinition();
        updated.setName("builtin-skill");
        assertThatThrownBy(() -> service.update("builtin-skill", updated, "updated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read-only");
        assertThatThrownBy(() -> service.delete("builtin-skill"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void rejectsRenameToExistingSkillBeforeDeletingOriginal() throws Exception {
        SkillFileRepository repository = new SkillFileRepository(tempDir, objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        SkillService service = new SkillService(repository, registry, objectMapper);

        SkillDefinition original = new SkillDefinition();
        original.setName("original");
        service.create(original, "original prompt");

        SkillDefinition existing = new SkillDefinition();
        existing.setName("existing");
        service.create(existing, "existing prompt");

        SkillDefinition renamed = new SkillDefinition();
        renamed.setName("existing");

        assertThatThrownBy(() -> service.update("original", renamed, "renamed prompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        assertThat(repository.exists("original")).isTrue();
        assertThat(registry.getDefinition("original")).isPresent();
    }

    private void writeRawSkill(Path skillDir, SkillDefinition definition, String prompt) throws Exception {
        Files.createDirectories(skillDir);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(skillDir.resolve("skill.json").toFile(), definition);
        Files.writeString(skillDir.resolve("SKILL.md"), prompt);
    }
}
