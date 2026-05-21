package top.lanshan.manmu.modelprovider;

import java.util.List;

public record ConfiguredModelProvider(String providerId, String displayName, String baseUrl, List<String> models) {

	public ConfiguredModelProvider {
		if (providerId == null || providerId.isBlank()) {
			throw new IllegalArgumentException("providerId must not be blank");
		}
		if (displayName == null || displayName.isBlank()) {
			throw new IllegalArgumentException("displayName must not be blank");
		}
		if (models == null || models.isEmpty()) {
			throw new IllegalArgumentException("models must not be empty");
		}
		models = List.copyOf(models);
	}

}
