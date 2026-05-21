package top.lanshan.manmu.modelprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ModelProviderKeyStoreTest {

	@TempDir
	Path tempDir;

	@Test
	void persistsAndReadsProviderApiKeysFromJson() throws Exception {
		Path file = tempDir.resolve(".local").resolve("model-providers.json");
		ModelProviderKeyStore store = new ModelProviderKeyStore(file, new ObjectMapper());

		store.saveApiKey("deepseek", "sk-test");

		ModelProviderKeyStore reloaded = new ModelProviderKeyStore(file, new ObjectMapper());
		assertThat(reloaded.getApiKey("deepseek")).contains("sk-test");
		assertThat(reloaded.hasApiKey("deepseek")).isTrue();
		assertThat(Files.readString(file)).contains("deepseek").contains("sk-test");
	}

}
