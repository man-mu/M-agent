package top.lanshan.manmu.skill.plugin;

import java.nio.file.Path;

public record SkillPluginDescriptor(String name, String version, Path packageDirectory,
        Path jarPath, String pluginClassName) {
}
