package top.lanshan.manmu.modelprovider;

import java.util.List;

public record ProviderSummary(String providerId, String displayName, List<String> models, boolean apiKeyConfigured) {
}
