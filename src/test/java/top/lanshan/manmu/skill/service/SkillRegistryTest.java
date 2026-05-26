package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    private SkillFileRepository fileRepository;
    private SkillRegistry registry;

    @BeforeEach
    void setUp() {
        fileRepository = new SkillFileRepository(tempDir, objectMapper);
        registry = new SkillRegistry(fileRepository);
    }

    @Test
    void loadsSkillsFromFiles() throws Exception {
        SkillDefinition def = new SkillDefinition();
        def.setName("skill-a");
        def.setDescription("A");
        def.setEnabled(true);
        fileRepository.writeSkill("skill-a", def, "prompt A");

        SkillDefinition def2 = new SkillDefinition();
        def2.setName("skill-b");
        def2.setDescription("B");
        def2.setEnabled(false);
        fileRepository.writeSkill("skill-b", def2, "prompt B");

        registry.loadAll();

        assertThat(registry.listAll()).hasSize(2);
        assertThat(registry.listEnabled()).hasSize(1);
        assertThat(registry.listEnabled().get(0).getName()).isEqualTo("skill-a");
    }

    @Test
    void registerAndUnregister() {
        SkillDefinition def = new SkillDefinition();
        def.setName("manual-skill");
        def.setEnabled(true);
        registry.register(def, "manual prompt");

        assertThat(registry.getDefinition("manual-skill")).isPresent();
        assertThat(registry.getPromptTemplate("manual-skill")).hasValue("manual prompt");

        registry.unregister("manual-skill");
        assertThat(registry.getDefinition("manual-skill")).isEmpty();
    }

    @Test
    void reloadsFromDisk() throws Exception {
        SkillDefinition def = new SkillDefinition();
        def.setName("reloadable");
        fileRepository.writeSkill("reloadable", def, "v1");
        registry.loadAll();

        SkillDefinition updated = new SkillDefinition();
        updated.setName("reloadable");
        updated.setDescription("updated");
        fileRepository.writeSkill("reloadable", updated, "v2");

        registry.reload();
        assertThat(registry.getDefinition("reloadable").orElseThrow().getDescription())
                .isEqualTo("updated");
    }
}
