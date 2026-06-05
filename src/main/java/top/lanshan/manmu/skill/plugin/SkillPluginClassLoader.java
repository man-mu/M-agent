package top.lanshan.manmu.skill.plugin;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

public class SkillPluginClassLoader extends URLClassLoader {

    private static final Set<String> SHARED_EXACT_CLASSES = Set.of(
            "top.lanshan.manmu.skill.service.SkillDefinition",
            "top.lanshan.manmu.skill.service.SkillStorageLocation",
            "top.lanshan.manmu.skill.market.SkillPackageType");

    private final ClassLoader sharedClassLoader;

    public SkillPluginClassLoader(URL[] urls, ClassLoader sharedClassLoader) {
        super(urls, ClassLoader.getPlatformClassLoader());
        this.sharedClassLoader = sharedClassLoader;
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
}
