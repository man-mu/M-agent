package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.lanshan.manmu.skill.market.SkillCatalogRepository;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@ConditionalOnProperty(prefix = "mvp.skill", name = "enabled", havingValue = "true")
public class SkillAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SkillAutoConfiguration.class);

    @Value("${mvp.skill.content-path:}")
    private String configuredContentPath;

    @Value("${mvp.skill.builtin-content-path:}")
    private String configuredBuiltinContentPath;

    @Value("${mvp.skill.local-market-path:}")
    private String configuredLocalMarketPath;

    @Bean
    SkillFileRepository skillFileRepository(ObjectMapper objectMapper) {
        Path builtinContentPath;
        if (configuredContentPath != null && !configuredContentPath.isBlank()) {
            builtinContentPath = Paths.get(configuredContentPath);
        } else if (configuredBuiltinContentPath != null && !configuredBuiltinContentPath.isBlank()) {
            builtinContentPath = Paths.get(configuredBuiltinContentPath);
        } else {
            builtinContentPath = Paths.get(userDir(),
                    "src/main/java/top/lanshan/manmu/skill/content");
        }
        Path localInstalledPath = localMarketPath().resolve("installed");
        logger.info("Built-in Skill content path: {}", builtinContentPath.toAbsolutePath());
        logger.info("Local Skill installed path: {}", localInstalledPath.toAbsolutePath());
        return new SkillFileRepository(builtinContentPath, localInstalledPath, objectMapper);
    }

    @Bean
    SkillCatalogRepository skillCatalogRepository(ObjectMapper objectMapper) {
        SkillCatalogRepository repository = new SkillCatalogRepository(localMarketPath(), objectMapper);
        repository.load();
        logger.info("Skill catalog path: {}", repository.catalogPath().toAbsolutePath());
        return repository;
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

    private Path localMarketPath() {
        if (configuredLocalMarketPath != null && !configuredLocalMarketPath.isBlank()) {
            return Paths.get(configuredLocalMarketPath);
        }
        return Paths.get(userDir(), ".local", "skills");
    }

    private String userDir() {
        return System.getProperty("user.dir");
    }

}
