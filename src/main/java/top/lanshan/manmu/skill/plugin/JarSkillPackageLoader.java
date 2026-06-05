package top.lanshan.manmu.skill.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.lanshan.manmu.skill.market.SkillPackageType;
import top.lanshan.manmu.skill.market.SkillPackageValidator;
import top.lanshan.manmu.skill.service.SkillDefinition;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.ServiceLoader;

public class JarSkillPackageLoader {

    private static final Logger logger = LoggerFactory.getLogger(JarSkillPackageLoader.class);
    public static final String PLUGIN_JAR = "plugin.jar";

    private final ClassLoader sharedClassLoader;

    public JarSkillPackageLoader(ClassLoader sharedClassLoader) {
        this.sharedClassLoader = sharedClassLoader;
    }

    public LoadedJarSkill load(SkillDefinition definition, Path packageDirectory) throws IOException {
        SkillPackageValidator.requireValidDefinition(definition);
        if (definition.getPackageType() != SkillPackageType.JAR) {
            throw new IllegalArgumentException("Skill '" + definition.getName() + "' is not a Jar Skill");
        }
        Path pluginJar = packageDirectory.resolve(PLUGIN_JAR).toAbsolutePath().normalize();
        if (!pluginJar.startsWith(packageDirectory.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Jar Skill path escapes package directory");
        }
        if (!Files.isRegularFile(pluginJar)) {
            throw new IllegalArgumentException("Jar Skill package must contain plugin.jar");
        }

        SkillPluginClassLoader classLoader = new SkillPluginClassLoader(
                new URL[] { pluginJar.toUri().toURL() }, sharedClassLoader);
        SkillPlugin plugin = null;
        try {
            ServiceLoader<SkillPlugin> serviceLoader = ServiceLoader.load(SkillPlugin.class, classLoader);
            Iterator<SkillPlugin> plugins = serviceLoader.iterator();
            if (!plugins.hasNext()) {
                throw new IllegalArgumentException("Jar Skill plugin.jar must declare " + SkillPlugin.SERVICE_DESCRIPTOR);
            }
            plugin = plugins.next();
            if (plugins.hasNext()) {
                throw new IllegalArgumentException("Jar Skill plugin.jar must declare exactly one SkillPlugin");
            }
            SkillDefinition pluginDefinition = plugin.definition();
            SkillPackageValidator.requireValidDefinition(pluginDefinition);
            if (!definition.getName().equals(pluginDefinition.getName())) {
                throw new IllegalArgumentException("Jar Skill plugin definition name must match skill.json");
            }
            SkillPluginDescriptor descriptor = new SkillPluginDescriptor(definition.getName(),
                    definition.getVersion(), packageDirectory.toAbsolutePath().normalize(),
                    pluginJar, plugin.getClass().getName());
            logger.info("Jar Skill loaded: {} ({})", definition.getName(), descriptor.pluginClassName());
            return new LoadedJarSkill(definition, plugin, descriptor, classLoader);
        } catch (RuntimeException e) {
            closeQuietly(plugin);
            try {
                classLoader.close();
            } catch (IOException ignored) {
                // best effort cleanup
            }
            throw e;
        }
    }

    private void closeQuietly(SkillPlugin plugin) {
        if (plugin == null) {
            return;
        }
        try {
            plugin.close();
        } catch (Exception ignored) {
            // best effort cleanup
        }
    }

    public record LoadedJarSkill(SkillDefinition definition, SkillPlugin plugin,
            SkillPluginDescriptor descriptor, SkillPluginClassLoader classLoader) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            try {
                plugin.close();
            } catch (Exception e) {
                throw new IOException("Failed to close Skill plugin", e);
            } finally {
                classLoader.close();
            }
        }
    }
}
