package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillFileRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    private SkillFileRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SkillFileRepository(tempDir, objectMapper);
    }

    @Test
    void listSkillNamesReturnsEmptyForEmptyDir() {
        assertThat(repository.listSkillNames()).isEmpty();
    }

    @Test
    void writesAndReadsSkillDefinition() throws Exception {
        SkillDefinition def = new SkillDefinition();
        def.setName("test-skill");
        def.setDescription("Test");
        def.setParameters(java.util.Map.of("type", "object"));

        repository.writeSkill("test-skill", def, "This is a prompt template.");

        assertThat(repository.exists("test-skill")).isTrue();
        assertThat(repository.listSkillNames()).contains("test-skill");

        SkillDefinition read = repository.readDefinition("test-skill").orElseThrow();
        assertThat(read.getName()).isEqualTo("test-skill");
        assertThat(read.getDescription()).isEqualTo("Test");

        String template = repository.readPromptTemplate("test-skill").orElseThrow();
        assertThat(template).isEqualTo("This is a prompt template.");
    }

    @Test
    void deletesSkill() throws Exception {
        SkillDefinition def = new SkillDefinition();
        def.setName("to-delete");
        repository.writeSkill("to-delete", def, "content");
        assertThat(repository.exists("to-delete")).isTrue();

        repository.deleteSkill("to-delete");
        assertThat(repository.exists("to-delete")).isFalse();
    }

    @Test
    void returnsEmptyForNonexistentSkill() {
        assertThat(repository.readDefinition("nonexistent")).isEmpty();
        assertThat(repository.readPromptTemplate("nonexistent")).isEmpty();
    }

    @Test
    void readsBuiltinAndLocalRootsWithLocationMetadata() throws Exception {
        Path builtin = tempDir.resolve("builtin");
        Path local = tempDir.resolve("local");
        SkillFileRepository multiRoot = new SkillFileRepository(builtin, local, objectMapper);

        SkillDefinition builtinDef = new SkillDefinition();
        builtinDef.setName("builtin-skill");
        builtinDef.setDescription("Built-in");
        writeRawSkill(builtin.resolve("builtin-skill"), builtinDef, "builtin prompt");

        SkillDefinition localDef = new SkillDefinition();
        localDef.setName("local-skill");
        localDef.setDescription("Local");
        multiRoot.writeSkill("local-skill", localDef, "local prompt");

        assertThat(multiRoot.listSkillNames()).containsExactly("builtin-skill", "local-skill");
        assertThat(multiRoot.readDefinition("builtin-skill").orElseThrow().getStorageLocation())
                .isEqualTo(SkillStorageLocation.BUILTIN);
        assertThat(multiRoot.readDefinition("local-skill").orElseThrow().getStorageLocation())
                .isEqualTo(SkillStorageLocation.LOCAL);
        assertThat(multiRoot.isBuiltin("builtin-skill")).isTrue();
        assertThat(multiRoot.isBuiltin("local-skill")).isFalse();
    }

    @Test
    void rejectsWritesToBuiltinSkills() throws Exception {
        Path builtin = tempDir.resolve("builtin");
        Path local = tempDir.resolve("local");
        SkillFileRepository multiRoot = new SkillFileRepository(builtin, local, objectMapper);

        SkillDefinition builtinDef = new SkillDefinition();
        builtinDef.setName("builtin-skill");
        writeRawSkill(builtin.resolve("builtin-skill"), builtinDef, "builtin prompt");

        SkillDefinition updated = new SkillDefinition();
        updated.setName("builtin-skill");

        assertThatThrownBy(() -> multiRoot.writeSkill("builtin-skill", updated, "updated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read-only");
        assertThatThrownBy(() -> multiRoot.deleteSkill("builtin-skill"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void rejectsInvalidSkillNames() {
        SkillDefinition def = new SkillDefinition();
        def.setName("../bad");

        assertThatThrownBy(() -> repository.writeSkill("../bad", def, "prompt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.exists("bad/name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.readDefinition("bad\\name"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void writeRawSkill(Path skillDir, SkillDefinition definition, String prompt) throws Exception {
        Files.createDirectories(skillDir);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(skillDir.resolve("skill.json").toFile(), definition);
        Files.writeString(skillDir.resolve("SKILL.md"), prompt);
    }
}
