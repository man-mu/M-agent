package top.lanshan.manmu.skill.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.lanshan.manmu.skill.service.SkillDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class SkillPluginRegistry implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SkillPluginRegistry.class);

    private final JarSkillPackageLoader loader;
    private final Map<String, JarSkillPackageLoader.LoadedJarSkill> plugins = new LinkedHashMap<>();

    public SkillPluginRegistry(JarSkillPackageLoader loader) {
        this.loader = loader;
    }

    public synchronized SkillPluginDescriptor register(SkillDefinition definition, Path packageDirectory)
            throws IOException {
        JarSkillPackageLoader.LoadedJarSkill loaded = loader.load(definition, packageDirectory);
        JarSkillPackageLoader.LoadedJarSkill previous = plugins.put(definition.getName(), loaded);
        closeQuietly(previous);
        return loaded.descriptor();
    }

    public synchronized Optional<SkillPluginDescriptor> descriptor(String name) {
        JarSkillPackageLoader.LoadedJarSkill loaded = plugins.get(name);
        return loaded == null ? Optional.empty() : Optional.of(loaded.descriptor());
    }

    public synchronized boolean hasPlugin(String name) {
        return plugins.containsKey(name);
    }

    public synchronized String invoke(String name, Map<String, Object> input) {
        JarSkillPackageLoader.LoadedJarSkill loaded = plugins.get(name);
        if (loaded == null) {
            throw new IllegalArgumentException("Jar Skill '" + name + "' is not loaded");
        }
        Map<String, Object> safeInput = input == null ? Map.of() : input;
        return loaded.plugin().execute(safeInput,
                new SkillPluginContext(loaded.definition(), loaded.descriptor().packageDirectory()));
    }

    public synchronized void unregister(String name) {
        closeQuietly(plugins.remove(name));
    }

    @Override
    public synchronized void close() {
        for (JarSkillPackageLoader.LoadedJarSkill loaded : plugins.values()) {
            closeQuietly(loaded);
        }
        plugins.clear();
    }

    private void closeQuietly(JarSkillPackageLoader.LoadedJarSkill loaded) {
        if (loaded == null) {
            return;
        }
        try {
            loaded.close();
        } catch (IOException e) {
            logger.warn("Failed to close Jar Skill '{}': {}", loaded.definition().getName(), e.getMessage());
        }
    }
}
