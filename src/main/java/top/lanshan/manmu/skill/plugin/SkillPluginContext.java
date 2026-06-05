package top.lanshan.manmu.skill.plugin;

import top.lanshan.manmu.skill.service.SkillDefinition;

import java.nio.file.Path;
import java.time.Instant;

public record SkillPluginContext(SkillDefinition definition, Path packageDirectory, Instant invokedAt) {

    public SkillPluginContext(SkillDefinition definition, Path packageDirectory) {
        this(definition, packageDirectory, Instant.now());
    }
}
