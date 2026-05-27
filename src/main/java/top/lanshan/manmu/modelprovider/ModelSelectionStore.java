package top.lanshan.manmu.modelprovider;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ModelSelectionStore {

	private final Path file;

	private final ObjectMapper objectMapper;

	public ModelSelectionStore(Path file, ObjectMapper objectMapper) {
		this.file = file;
		this.objectMapper = objectMapper;
	}

	public synchronized Optional<ModelSelection> read() {
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		try {
			ModelSelection selection = objectMapper.readValue(file.toFile(), ModelSelection.class);
			if (isBlank(selection.providerId()) || isBlank(selection.modelName())) {
				return Optional.empty();
			}
			return Optional.of(selection);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read current model selection from " + file, ex);
		}
	}

	public synchronized void save(String providerId, String modelName) {
		try {
			Files.createDirectories(file.getParent());
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), new ModelSelection(providerId, modelName));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write current model selection to " + file, ex);
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	public record ModelSelection(String providerId, String modelName) {
	}

}
