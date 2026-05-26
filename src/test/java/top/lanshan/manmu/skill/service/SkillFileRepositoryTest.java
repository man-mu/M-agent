package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
}
