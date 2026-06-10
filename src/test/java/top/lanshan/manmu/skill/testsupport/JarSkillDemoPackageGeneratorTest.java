package top.lanshan.manmu.skill.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JarSkillDemoPackageGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @EnabledIfSystemProperty(named = "mvp.demo.jar-skill-package", matches = "true")
    void writesEchoJsonSkillDemoPackage() throws Exception {
        Path outputDir = Path.of("target", "demo-packages");
        Files.createDirectories(outputDir);
        byte[] zip = JarSkillPackageTestSupport.jarSkillPackage(objectMapper,
                Path.of("target", "demo-jar-skill-work"), "echo-json-skill", true);
        Path output = outputDir.resolve("echo-json-skill.zip");
        Files.write(output, zip);

        assertThat(output).exists().isRegularFile();
        assertThat(Files.size(output)).isGreaterThan(0);
    }
}
