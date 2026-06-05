package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.lanshan.manmu.skill.market.SkillCatalogRepository;
import top.lanshan.manmu.skill.market.SkillPackageArchiveService;
import top.lanshan.manmu.skill.market.SkillPackageType;
import top.lanshan.manmu.skill.plugin.JarSkillPackageLoader;
import top.lanshan.manmu.skill.plugin.SkillPluginRegistry;
import top.lanshan.manmu.skill.testsupport.JarSkillPackageTestSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SkillPackageArchiveService archiveService = new SkillPackageArchiveService(objectMapper);

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

    @Test
    void importsExportsReloadsAndUninstallsPromptPackage() throws Exception {
        Path builtin = tempDir.resolve("builtin");
        Path local = tempDir.resolve("local");
        Path market = tempDir.resolve("market");
        SkillFileRepository repository = new SkillFileRepository(builtin, local, objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        SkillCatalogRepository catalogRepository = new SkillCatalogRepository(market, objectMapper);
        SkillService service = new SkillService(repository, registry, objectMapper,
                archiveService, catalogRepository);

        SkillDefinition packaged = new SkillDefinition();
        packaged.setName("sample-skill");
        packaged.setDescription("Packaged prompt skill");
        packaged.setVersion("1.2.0");
        packaged.setEnabled(true);
        byte[] zip = archiveService.writePromptPackage(packaged, "Prompt {{topic}}");

        var result = service.importPromptPackage("sample-skill.zip", zip);

        assertThat(result.getName()).isEqualTo("sample-skill");
        assertThat(result.getPackageType()).isEqualTo(SkillPackageType.PROMPT);
        assertThat(repository.localSkillPath("sample-skill")).exists();
        assertThat(registry.getDefinition("sample-skill")).isPresent();
        assertThat(service.renderSkill("sample-skill", java.util.Map.of("topic", "zip")))
                .isEqualTo("Prompt zip");
        assertThat(catalogRepository.load()).singleElement()
                .extracting(entry -> entry.getName())
                .isEqualTo("sample-skill");

        byte[] exported = service.exportPromptPackage("sample-skill");
        SkillPackageArchiveService.PromptSkillPackage exportedPackage =
                archiveService.readPromptPackage("exported.zip", exported);
        assertThat(exportedPackage.definition().getName()).isEqualTo("sample-skill");
        assertThat(exportedPackage.promptTemplate()).isEqualTo("Prompt {{topic}}");

        SkillDefinition changed = repository.readLocalDefinition("sample-skill").orElseThrow();
        changed.setDescription("Reloaded description");
        repository.writeSkill("sample-skill", changed, "Reloaded {{topic}}");
        service.reload("sample-skill");
        assertThat(service.renderSkill("sample-skill", java.util.Map.of("topic", "disk")))
                .isEqualTo("Reloaded disk");

        service.uninstallPackage("sample-skill");
        assertThat(repository.exists("sample-skill")).isFalse();
        assertThat(registry.getDefinition("sample-skill")).isEmpty();
        assertThat(catalogRepository.load()).isEmpty();
    }

    @Test
    void packageLifecycleRejectsBuiltInMutation() throws Exception {
        Path builtin = tempDir.resolve("builtin");
        Path local = tempDir.resolve("local");
        SkillDefinition definition = new SkillDefinition();
        definition.setName("builtin-skill");
        writeRawSkill(builtin.resolve("builtin-skill"), definition, "prompt");

        SkillFileRepository repository = new SkillFileRepository(builtin, local, objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        registry.loadAll();
        SkillService service = new SkillService(repository, registry, objectMapper);

        SkillDefinition packaged = new SkillDefinition();
        packaged.setName("builtin-skill");
        packaged.setDescription("Cannot overwrite built-in");
        byte[] zip = archiveService.writePromptPackage(packaged, "packaged");

        assertThatThrownBy(() -> service.importPromptPackage("builtin-skill.zip", zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read-only");
        assertThatThrownBy(() -> service.uninstallPackage("builtin-skill"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void rejectsJarPackageImportWhenDisabled() throws Exception {
        SkillFileRepository repository = new SkillFileRepository(tempDir.resolve("skills"), objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        SkillService service = new SkillService(repository, registry, objectMapper);
        byte[] zip = JarSkillPackageTestSupport.jarSkillPackage(objectMapper, tempDir, "echo-json-skill", true);

        assertThatThrownBy(() -> service.importJarPackage("echo-json-skill.zip", zip))
                .isInstanceOf(SkillService.JarPluginsDisabledException.class)
                .hasMessageContaining("disabled");
        assertThat(repository.exists("echo-json-skill")).isFalse();
        assertThat(registry.getDefinition("echo-json-skill")).isEmpty();
    }

    @Test
    void importsInvokesTogglesReloadsAndUninstallsJarPackage() throws Exception {
        Path builtin = tempDir.resolve("builtin");
        Path local = tempDir.resolve("local");
        Path market = tempDir.resolve("market");
        Path closeMarker = tempDir.resolve("jar-close-marker.txt");
        System.setProperty("jarSkillCloseMarker", closeMarker.toString());
        SkillPluginRegistry pluginRegistry = new SkillPluginRegistry(
                new JarSkillPackageLoader(SkillPluginRegistry.class.getClassLoader()));
        try {
            SkillFileRepository repository = new SkillFileRepository(builtin, local, objectMapper);
            SkillRegistry registry = new SkillRegistry(repository);
            SkillCatalogRepository catalogRepository = new SkillCatalogRepository(market, objectMapper);
            SkillService service = new SkillService(repository, registry, objectMapper,
                    archiveService, catalogRepository, pluginRegistry, true);
            byte[] zip = JarSkillPackageTestSupport.jarSkillPackage(objectMapper, tempDir,
                    "echo-json-skill", true);

            var result = service.importJarPackage("echo-json-skill.zip", zip);

            assertThat(result.getName()).isEqualTo("echo-json-skill");
            assertThat(result.getPackageType()).isEqualTo(SkillPackageType.JAR);
            assertThat(repository.localSkillPath("echo-json-skill").resolve("plugin.jar")).exists();
            assertThat(repository.localSkillPath("echo-json-skill").resolve("SKILL.md")).doesNotExist();
            assertThat(registry.getDefinition("echo-json-skill")).isPresent();
            assertThat(pluginRegistry.hasPlugin("echo-json-skill")).isTrue();
            assertThat(pluginRegistry.invoke("echo-json-skill", Map.of("message", "hello")))
                    .contains("echo:hello")
                    .contains("SkillPluginClassLoader");
            assertThat(service.renderSkill("echo-json-skill", Map.of("message", "hello"))).isNull();

            service.toggle("echo-json-skill");
            assertThat(registry.getDefinition("echo-json-skill").orElseThrow().isEnabled()).isFalse();
            assertThat(pluginRegistry.hasPlugin("echo-json-skill")).isFalse();
            assertThat(closeMarker).exists();

            Files.deleteIfExists(closeMarker);
            service.toggle("echo-json-skill");
            assertThat(registry.getDefinition("echo-json-skill").orElseThrow().isEnabled()).isTrue();
            assertThat(pluginRegistry.invoke("echo-json-skill", Map.of("message", "again")))
                    .contains("echo:again");

            service.reload("echo-json-skill");
            assertThat(pluginRegistry.hasPlugin("echo-json-skill")).isTrue();

            service.uninstallPackage("echo-json-skill");
            assertThat(repository.exists("echo-json-skill")).isFalse();
            assertThat(registry.getDefinition("echo-json-skill")).isEmpty();
            assertThat(pluginRegistry.hasPlugin("echo-json-skill")).isFalse();
            assertThat(catalogRepository.load()).isEmpty();
            assertThat(closeMarker).exists();
        } finally {
            pluginRegistry.close();
            System.clearProperty("jarSkillCloseMarker");
        }
    }

    @Test
    void rejectsInvalidJarPackageAndCleansInstallDirectory() throws Exception {
        SkillFileRepository repository = new SkillFileRepository(tempDir.resolve("skills"), objectMapper);
        SkillRegistry registry = new SkillRegistry(repository);
        SkillPluginRegistry pluginRegistry = new SkillPluginRegistry(
                new JarSkillPackageLoader(SkillPluginRegistry.class.getClassLoader()));
        SkillService service = new SkillService(repository, registry, objectMapper,
                archiveService, null, pluginRegistry, true);
        byte[] zip = JarSkillPackageTestSupport.invalidJarSkillPackage(objectMapper, "broken-jar-skill");

        assertThatThrownBy(() -> service.importJarPackage("broken-jar-skill.zip", zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must declare");
        assertThat(repository.exists("broken-jar-skill")).isFalse();
        assertThat(registry.getDefinition("broken-jar-skill")).isEmpty();
        assertThat(pluginRegistry.hasPlugin("broken-jar-skill")).isFalse();
    }

    private void writeRawSkill(Path skillDir, SkillDefinition definition, String prompt) throws Exception {
        Files.createDirectories(skillDir);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(skillDir.resolve("skill.json").toFile(), definition);
        Files.writeString(skillDir.resolve("SKILL.md"), prompt);
    }
}
