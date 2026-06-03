package top.lanshan.manmu.skill.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillCatalogRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void createsEmptyCatalogWhenMissing() {
        SkillCatalogRepository repository = new SkillCatalogRepository(tempDir, objectMapper);

        assertThat(repository.load()).isEmpty();
        assertThat(Files.exists(tempDir.resolve("catalog.json"))).isTrue();
    }

    @Test
    void upsertsAndRemovesEntries() {
        SkillCatalogRepository repository = new SkillCatalogRepository(tempDir, objectMapper);

        SkillCatalogEntry entry = new SkillCatalogEntry();
        entry.setName("sample-skill");
        entry.setVersion("1.2.0");
        entry.setPackageType(SkillPackageType.PROMPT);
        entry.setStatus(SkillPackageStatus.INSTALLED);
        repository.upsert(entry);

        assertThat(repository.load()).singleElement()
                .satisfies(loaded -> {
                    assertThat(loaded.getName()).isEqualTo("sample-skill");
                    assertThat(loaded.getVersion()).isEqualTo("1.2.0");
                    assertThat(loaded.getInstalledAt()).isNotNull();
                    assertThat(loaded.getUpdatedAt()).isNotNull();
                });

        SkillCatalogEntry updated = new SkillCatalogEntry();
        updated.setName("sample-skill");
        updated.setVersion("1.3.0");
        repository.upsert(updated);
        assertThat(repository.load()).singleElement()
                .extracting(SkillCatalogEntry::getVersion)
                .isEqualTo("1.3.0");

        repository.remove("sample-skill");
        assertThat(repository.load()).isEmpty();
    }

    @Test
    void rejectsInvalidNames() {
        SkillCatalogRepository repository = new SkillCatalogRepository(tempDir, objectMapper);
        SkillCatalogEntry entry = new SkillCatalogEntry();
        entry.setName("../bad");

        assertThatThrownBy(() -> repository.upsert(entry))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
