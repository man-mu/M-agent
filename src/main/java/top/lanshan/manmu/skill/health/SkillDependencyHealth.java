package top.lanshan.manmu.skill.health;

import java.util.List;

public record SkillDependencyHealth(String name, String type, boolean available,
        String message, List<String> matchedServers, List<String> requiredEnvVars,
        Boolean keyConfigured) {
}
