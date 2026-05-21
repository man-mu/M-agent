package top.lanshan.manmu.modelprovider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ModelProviderKeyStore {

	private static final TypeReference<Map<String, String>> KEY_MAP_TYPE = new TypeReference<>() {
	};

	private final Path file;

	private final ObjectMapper objectMapper;

	public ModelProviderKeyStore(Path file, ObjectMapper objectMapper) {
		this.file = file;
		this.objectMapper = objectMapper;
	}

	public synchronized Optional<String> getApiKey(String providerId) {
		return Optional.ofNullable(readAll().get(providerId));
	}

	public synchronized boolean hasApiKey(String providerId) {
		return getApiKey(providerId).filter(key -> !key.isBlank()).isPresent();
	}

	public synchronized void saveApiKey(String providerId, String apiKey) {
		Map<String, String> keys = readAll();
		keys.put(providerId, apiKey);
		writeAll(keys);
	}

	private Map<String, String> readAll() {
		if (!Files.exists(file)) {
			return new HashMap<>();
		}
		try {
			Map<String, String> keys = objectMapper.readValue(file.toFile(), KEY_MAP_TYPE);
			return new HashMap<>(keys);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read model provider keys from " + file, ex);
		}
	}

	private void writeAll(Map<String, String> keys) {
		try {
			Files.createDirectories(file.getParent());
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), keys);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write model provider keys to " + file, ex);
		}
	}

}
