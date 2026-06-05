package top.lanshan.manmu.skill.health;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillInvocationHistoryServiceTest {

    @Test
    void recordsRecentInvocationsWithSanitizedSensitiveFields() {
        SkillInvocationHistoryService service = new SkillInvocationHistoryService();

        service.record("weather-now", "TOOL",
                Map.of("apiKey", "secret-key", "nested", Map.of("token", "secret-token")),
                "ok token=secret-token", "", 42);

        var records = service.recent("weather-now", 10);

        assertThat(records).hasSize(1);
        SkillInvocationRecord record = records.get(0);
        assertThat(record.success()).isTrue();
        assertThat(record.input().toString()).contains("***");
        assertThat(record.input().toString()).doesNotContain("secret-key");
        assertThat(record.input().toString()).doesNotContain("secret-token");
        assertThat(record.output()).contains("token=***");
        assertThat(record.durationMs()).isEqualTo(42);
    }

    @Test
    void keepsNewestRecordsWithinPerSkillLimit() {
        SkillInvocationHistoryService service = new SkillInvocationHistoryService();

        for (int i = 0; i < SkillInvocationHistoryService.MAX_RECORDS_PER_SKILL + 5; i++) {
            service.record("code-review", "TOOL", Map.of("index", i), "output " + i, "", 1);
        }

        var records = service.recent("code-review", 200);

        assertThat(records).hasSize(SkillInvocationHistoryService.MAX_RECORDS_PER_SKILL);
        assertThat(records.get(0).output()).isEqualTo("output 104");
        assertThat(records.get(records.size() - 1).output()).isEqualTo("output 5");
    }
}
