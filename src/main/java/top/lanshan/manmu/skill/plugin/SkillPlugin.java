package top.lanshan.manmu.skill.plugin;

import top.lanshan.manmu.skill.service.SkillDefinition;

import java.util.Map;

public interface SkillPlugin extends AutoCloseable {

    String SERVICE_DESCRIPTOR = "META-INF/services/top.lanshan.manmu.skill.plugin.SkillPlugin";

    SkillDefinition definition();

    String execute(Map<String, Object> input, SkillPluginContext context);

    @Override
    default void close() {
    }
}
