package top.lanshan.manmu.skill.health;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SkillInvocationHistoryService {

    static final int MAX_RECORDS_PER_SKILL = 100;
    static final int MAX_TEXT_CHARS = 4096;

    private final Map<String, ArrayDeque<SkillInvocationRecord>> records = new LinkedHashMap<>();

    public SkillInvocationRecord record(String skillName, String source,
            Map<String, Object> input, String output, String error, long durationMs) {
        String normalizedName = normalizeName(skillName);
        boolean success = error == null || error.isBlank();
        SkillInvocationRecord record = new SkillInvocationRecord(UUID.randomUUID().toString(),
                normalizedName, normalizeSource(source), Instant.now(), success,
                sanitizeInput(input == null ? Map.of() : input),
                limit(sanitizeText(output)), limit(sanitizeText(error)), Math.max(0, durationMs));
        synchronized (records) {
            ArrayDeque<SkillInvocationRecord> queue =
                    records.computeIfAbsent(normalizedName, ignored -> new ArrayDeque<>());
            queue.addFirst(record);
            while (queue.size() > MAX_RECORDS_PER_SKILL) {
                queue.removeLast();
            }
        }
        return record;
    }

    public long durationMs(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    public List<SkillInvocationRecord> recent(String skillName, int limit) {
        String normalizedName = normalizeName(skillName);
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, MAX_RECORDS_PER_SKILL);
        synchronized (records) {
            ArrayDeque<SkillInvocationRecord> queue = records.get(normalizedName);
            if (queue == null || queue.isEmpty()) {
                return List.of();
            }
            return queue.stream().limit(safeLimit).toList();
        }
    }

    private String normalizeName(String skillName) {
        String value = skillName == null ? "" : skillName.strip();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Skill name must not be empty");
        }
        return value;
    }

    private String normalizeSource(String source) {
        return source == null || source.isBlank() ? "UNKNOWN" : source.strip();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeInput(Map<String, Object> input) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSensitiveKey(key)) {
                sanitized.put(key, "***");
            } else if (value instanceof Map<?, ?> nested) {
                sanitized.put(key, sanitizeInput((Map<String, Object>) nested));
            } else {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("key")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("password")
                || lower.contains("credential");
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("(?i)(key|token|api[_-]?key|access[_-]?key|password|secret)=([^&\\s]+)", "$1=***")
                .replaceAll("(?i)(\"(?:key|token|api[_-]?key|access[_-]?key|password|secret)\"\\s*:\\s*\")([^\"]+)(\")", "$1***$3");
    }

    private String limit(String value) {
        if (value == null || value.length() <= MAX_TEXT_CHARS) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_TEXT_CHARS) + "\n...[truncated]";
    }
}
