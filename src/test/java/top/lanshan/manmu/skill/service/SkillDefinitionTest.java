package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.lanshan.manmu.skill.market.SkillPackageType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillDefinitionTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializesFromJson() throws Exception {
        String json = """
                {
                    "name": "test-skill",
                    "description": "A test skill",
                    "version": "2.0.0",
                    "enabled": true,
                    "parameters": {"type": "object"},
                    "dependencies": ["java"]
                }""";

        SkillDefinition def = objectMapper.readValue(json, SkillDefinition.class);

        assertThat(def.getName()).isEqualTo("test-skill");
        assertThat(def.getDescription()).isEqualTo("A test skill");
        assertThat(def.getVersion()).isEqualTo("2.0.0");
        assertThat(def.isEnabled()).isTrue();
        assertThat(def.getDependencies()).containsExactly("java");
    }

    @Test
    void getInputSchemaJsonReturnsEmptyForNullParams() {
        SkillDefinition def = new SkillDefinition();
        assertThat(def.getInputSchemaJson()).isEqualTo("{}");
    }

    @Test
    void getInputSchemaJsonReturnsJsonForParams() {
        SkillDefinition def = new SkillDefinition();
        def.setParameters(Map.of("type", "object", "properties", Map.of("x", Map.of("type", "string"))));

        String schema = def.getInputSchemaJson();
        assertThat(schema).contains("\"type\"");
        assertThat(schema).contains("\"string\"");
    }

    @Test
    void deserializesMarketMetadata() throws Exception {
        String json = """
                {
                    "name": "market-skill",
                    "description": "A market skill",
                    "displayName": "Market Skill",
                    "category": "tools",
                    "author": "local",
                    "homepage": "https://example.com",
                    "tags": ["market", "prompt"],
                    "packageType": "PROMPT",
                    "source": "local",
                    "installed_at": "2026-06-03T10:00:00Z",
                    "updated_at": "2026-06-03T11:00:00Z"
                }""";

        SkillDefinition def = objectMapper.readValue(json, SkillDefinition.class);

        assertThat(def.getDisplayName()).isEqualTo("Market Skill");
        assertThat(def.getCategory()).isEqualTo("tools");
        assertThat(def.getAuthor()).isEqualTo("local");
        assertThat(def.getHomepage()).isEqualTo("https://example.com");
        assertThat(def.getTags()).containsExactly("market", "prompt");
        assertThat(def.getPackageType()).isEqualTo(SkillPackageType.PROMPT);
        assertThat(def.getSource()).isEqualTo("local");
        assertThat(def.getInstalledAt()).isNotNull();
        assertThat(def.getUpdatedAt()).isNotNull();
    }
}
