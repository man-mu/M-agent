package top.lanshan.manmu.skill.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.lanshan.manmu.skill.market.SkillPackageType;
import top.lanshan.manmu.skill.plugin.SkillPlugin;
import top.lanshan.manmu.skill.service.SkillDefinition;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public final class JarSkillPackageTestSupport {

    private JarSkillPackageTestSupport() {
    }

    public static byte[] jarSkillPackage(ObjectMapper objectMapper, Path workDir, String name, boolean enabled)
            throws Exception {
        SkillDefinition definition = jarDefinition(name, enabled);
        return zipWith(objectMapper.writeValueAsBytes(definition), pluginJar(workDir, name), "Trusted local Jar Skill");
    }

    public static byte[] invalidJarSkillPackage(ObjectMapper objectMapper, String name) throws Exception {
        SkillDefinition definition = jarDefinition(name, true);
        return zipWith(objectMapper.writeValueAsBytes(definition), emptyJar(), null);
    }

    private static SkillDefinition jarDefinition(String name, boolean enabled) {
        SkillDefinition definition = new SkillDefinition();
        definition.setName(name);
        definition.setDescription("Echo JSON Jar Skill");
        definition.setVersion("1.0.0");
        definition.setEnabled(enabled);
        definition.setPackageType(SkillPackageType.JAR);
        definition.setParameters(Map.of(
                "type", "object",
                "properties", Map.of("message", Map.of("type", "string")),
                "required", java.util.List.of("message")));
        return definition;
    }

    private static byte[] pluginJar(Path workDir, String skillName) throws Exception {
        Path srcDir = workDir.resolve("plugin-src");
        Path classesDir = workDir.resolve("plugin-classes");
        Files.createDirectories(srcDir.resolve("example"));
        Files.createDirectories(classesDir);
        Path source = srcDir.resolve("example").resolve("EchoJsonSkill.java");
        Files.writeString(source, pluginSource(skillName), StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler is available").isNotNull();
        int exitCode = compiler.run(null, null, null,
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDir.toString(),
                source.toString());
        assertThat(exitCode).as("plugin source compiles").isZero();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                JarOutputStream jar = new JarOutputStream(out)) {
            addJarEntry(jar, "example/EchoJsonSkill.class",
                    Files.readAllBytes(classesDir.resolve("example").resolve("EchoJsonSkill.class")));
            addJarEntry(jar, SkillPlugin.SERVICE_DESCRIPTOR,
                    "example.EchoJsonSkill\n".getBytes(StandardCharsets.UTF_8));
            jar.finish();
            return out.toByteArray();
        }
    }

    private static String pluginSource(String skillName) {
        return """
                package example;

                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.Map;
                import top.lanshan.manmu.skill.plugin.SkillPlugin;
                import top.lanshan.manmu.skill.plugin.SkillPluginContext;
                import top.lanshan.manmu.skill.service.SkillDefinition;

                public class EchoJsonSkill implements SkillPlugin {
                    @Override
                    public SkillDefinition definition() {
                        SkillDefinition definition = new SkillDefinition();
                        definition.setName("%s");
                        definition.setDescription("Echo JSON Jar Skill");
                        definition.setVersion("1.0.0");
                        return definition;
                    }

                    @Override
                    public String execute(Map<String, Object> input, SkillPluginContext context) {
                        Object message = input == null ? "" : input.getOrDefault("message", "");
                        return "echo:" + message + "|loader=" + getClass().getClassLoader().getClass().getName();
                    }

                    @Override
                    public void close() {
                        String marker = System.getProperty("jarSkillCloseMarker");
                        if (marker == null || marker.isBlank()) {
                            return;
                        }
                        try {
                            Files.writeString(Path.of(marker), "closed");
                        } catch (Exception ignored) {
                        }
                    }
                }
                """.formatted(skillName);
    }

    private static byte[] emptyJar() throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                JarOutputStream jar = new JarOutputStream(out)) {
            jar.finish();
            return out.toByteArray();
        }
    }

    private static byte[] zipWith(byte[] skillJson, byte[] pluginJar, String readme) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("skill.json"));
            zip.write(skillJson);
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("plugin.jar"));
            zip.write(pluginJar);
            zip.closeEntry();

            if (readme != null) {
                zip.putNextEntry(new ZipEntry("README.md"));
                zip.write(readme.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        }
    }

    private static void addJarEntry(JarOutputStream jar, String name, byte[] content) throws Exception {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content);
        jar.closeEntry();
    }
}
