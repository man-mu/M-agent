package top.lanshan.manmu.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PgVectorStoreConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues("spring.autoconfigure.exclude="
            + "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration");

    @Test
    void pgVectorStoreIsNotCreatedWhenRagDisabled() {
        contextRunner
            .withPropertyValues("mvp.rag.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(PgVectorStore.class));
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {
    }

}
