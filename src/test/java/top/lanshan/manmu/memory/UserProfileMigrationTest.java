package top.lanshan.manmu.memory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileMigrationTest {

	@Test
	void v6MigrationAddsStructuredProfileColumns() throws Exception {
		String sql = Files.readString(Path.of("src/main/resources/db/migration/V6__alter_user_profiles.sql"));

		assertThat(sql).contains("ALTER TABLE user_profiles");
		assertThat(sql).contains("ADD COLUMN IF NOT EXISTS expertise_level VARCHAR(32)");
		assertThat(sql).contains("ADD COLUMN IF NOT EXISTS detail_preference VARCHAR(32)");
		assertThat(sql).contains("ADD COLUMN IF NOT EXISTS style_preference VARCHAR(32)");
	}

}
