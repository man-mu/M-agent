package top.lanshan.manmu.skill.plugin;

import java.util.Map;

public record SkillPluginResult(String output, Map<String, Object> metadata) {

    public static SkillPluginResult of(String output) {
        return new SkillPluginResult(output, Map.of());
    }
}
