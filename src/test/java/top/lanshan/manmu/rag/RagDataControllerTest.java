package top.lanshan.manmu.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagDataControllerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class);

    @Test
    void ingestionServiceIsAvailableWhenRagEnabled() {
        contextRunner
            .withPropertyValues("mvp.rag.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(VectorStoreDataIngestionService.class));
    }

    @Configuration
    static class TestConfig {
        @Bean
        VectorStoreDataIngestionService ingestionService() {
            return new VectorStoreDataIngestionService(new StubVectorStore());
        }
    }

    static class StubVectorStore implements VectorStore {
        final List<Document> acceptedDocuments = new ArrayList<>();

        @Override
        public void add(List<Document> documents) {
            acceptedDocuments.addAll(documents);
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }

        @Override
        public List<Document> similaritySearch(String query) {
            return List.of();
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }
    }

}
