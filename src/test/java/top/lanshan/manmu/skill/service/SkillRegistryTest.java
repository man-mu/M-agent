package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
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

    @Test
    void builtInDemoSkillsCoverWeatherCalculationAndWebSearch() {
        Path builtin = Path.of("src/main/java/top/lanshan/manmu/skill/content");
        SkillFileRepository repository = new SkillFileRepository(builtin, tempDir.resolve("local"), objectMapper);
        SkillRegistry builtInRegistry = new SkillRegistry(repository);

        builtInRegistry.loadAll();

        assertThat(builtInRegistry.listAll()).extracting(SkillDefinition::getName)
                .contains("weather-now", "calculator", "web-search");

        List<SkillDefinition> demoSkills = builtInRegistry.listAll().stream()
                .filter(skill -> List.of("weather-now", "calculator", "web-search").contains(skill.getName()))
                .toList();
        assertThat(demoSkills).hasSize(3)
                .allSatisfy(skill -> {
                    assertThat(skill.isEnabled()).isTrue();
                    assertThat(skill.getVersion()).isNotBlank();
                    assertThat(skill.getDescription()).isNotBlank();
                    assertThat(skill.getCategory()).isNotBlank();
                    assertThat(skill.getTags()).isNotEmpty();
                    assertThat(skill.getParameters()).containsEntry("type", "object");
                    assertThat(builtInRegistry.getPromptTemplate(skill.getName())).isPresent();
                });

        assertThat(builtInRegistry.getDefinition("weather-now").orElseThrow().getDependencies())
                .contains("mcp-qweather");
        assertThat(builtInRegistry.getDefinition("web-search").orElseThrow().getDependencies())
                .contains("search-bocha");
        assertThat(builtInRegistry.getDefinition("calculator").orElseThrow().getDependencies())
                .isEmpty();
    }
}
