package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@ConditionalOnProperty(prefix = "mvp.skill", name = "enabled", havingValue = "true")
public class SkillAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SkillAutoConfiguration.class);

    @Value("${mvp.skill.content-path:}")
    private String configuredContentPath;

    @Bean
    SkillFileRepository skillFileRepository(ObjectMapper objectMapper) {
        Path contentPath;
        if (configuredContentPath != null && !configuredContentPath.isBlank()) {
            contentPath = Paths.get(configuredContentPath);
        } else {
            String userDir = System.getProperty("user.dir");
            contentPath = Paths.get(userDir,
                    "src/main/java/top/lanshan/manmu/skill/content");
        }
        logger.info("Skill content path: {}", contentPath.toAbsolutePath());
        return new SkillFileRepository(contentPath, objectMapper);
    }

    @Bean
    SkillRegistry skillRegistry(SkillFileRepository fileRepository) {
        SkillRegistry registry = new SkillRegistry(fileRepository);
        registry.loadAll();
        return registry;
    }

    @Bean
    SkillToolProvider skillToolProvider(SkillRegistry registry, ObjectMapper objectMapper) {
        return new SkillToolProvider(registry, objectMapper);
    }

    @Bean
    SkillService skillService(SkillFileRepository fileRepository, SkillRegistry registry,
            ObjectMapper objectMapper) {
        return new SkillService(fileRepository, registry, objectMapper);
    }

    @Bean
    SkillController skillController(SkillService skillService) {
        return new SkillController(skillService);
    }
}
