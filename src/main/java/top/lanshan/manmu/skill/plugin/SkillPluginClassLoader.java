package top.lanshan.manmu.skill.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * Custom ClassLoader for JAR Skill plugins.
 * <p>
 * Copies the plugin JAR to a temporary directory to avoid file locking on Windows.
 * The original JAR file remains unlocked and can be deleted at any time.
 * Temporary files are cleaned up when {@link #close()} is called.
 */
public class SkillPluginClassLoader extends URLClassLoader {

    private static final Logger logger = LoggerFactory.getLogger(SkillPluginClassLoader.class);

    private static final Set<String> SHARED_EXACT_CLASSES = Set.of(
            "top.lanshan.manmu.skill.service.SkillDefinition",
            "top.lanshan.manmu.skill.service.SkillStorageLocation",
            "top.lanshan.manmu.skill.market.SkillPackageType");

    private final ClassLoader sharedClassLoader;
    private final Path tempJarFile;

    /**
     * Creates a ClassLoader that loads classes from a copy of the given JAR file.
     * The original JAR is copied to a temporary location so it is not locked.
     *
     * @param pluginJar        the original plugin JAR file
     * @param sharedClassLoader the classloader for shared API classes
     * @throws IOException if the JAR cannot be copied
     */
    public SkillPluginClassLoader(Path pluginJar, ClassLoader sharedClassLoader) throws IOException {
        super(new URL[] { copyToTemp(pluginJar).toUri().toURL() }, ClassLoader.getPlatformClassLoader());
        this.sharedClassLoader = sharedClassLoader;
        this.tempJarFile = resolveTempJarPath(pluginJar);
        copyToTemp(pluginJar, tempJarFile);
    }

    /**
     * Legacy constructor for backward compatibility (e.g. tests).
     */
    public SkillPluginClassLoader(URL[] urls, ClassLoader sharedClassLoader) {
        super(urls, ClassLoader.getPlatformClassLoader());
        this.sharedClassLoader = sharedClassLoader;
        this.tempJarFile = null;
    }

    @Override
    public void close() throws IOException {
        super.close();
        deleteTempJar();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = loadSharedClass(name);
            }
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException ignored) {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private Class<?> loadSharedClass(String name) throws ClassNotFoundException {
        if (name.startsWith("top.lanshan.manmu.skill.plugin.") || SHARED_EXACT_CLASSES.contains(name)) {
            return sharedClassLoader.loadClass(name);
        }
        return null;
    }

    private static Path resolveTempJarPath(Path pluginJar) {
        String fileName = pluginJar.getFileName().toString();
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "m-agent-skill-plugins");
        return tempDir.resolve(fileName + "-" + System.identityHashCode(pluginJar));
    }

    private static Path copyToTemp(Path pluginJar) throws IOException {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "m-agent-skill-plugins");
        Files.createDirectories(tempDir);
        Path tempJar = tempDir.resolve(
                pluginJar.getFileName().toString() + "-" + System.identityHashCode(pluginJar));
        Files.copy(pluginJar, tempJar, StandardCopyOption.REPLACE_EXISTING);
        return tempJar;
    }

    private static void copyToTemp(Path pluginJar, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(pluginJar, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteTempJar() {
        if (tempJarFile != null) {
            try {
                Files.deleteIfExists(tempJarFile);
                logger.debug("Cleaned up temp JAR: {}", tempJarFile);
            } catch (IOException e) {
                logger.warn("Failed to delete temp JAR '{}': {}", tempJarFile, e.getMessage());
            }
            // Best-effort cleanup of temp dir if empty
            try {
                Files.deleteIfExists(tempJarFile.getParent());
            } catch (IOException ignored) {
                // temp dir not empty, that's fine
            }
        }
    }
}
