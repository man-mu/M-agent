package top.lanshan.manmu.config;

import javax.sql.DataSource;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(prefix = "mvp.rag", name = "enabled", havingValue = "true")
public class PgVectorStoreConfiguration {

    @Bean
    DataSource vectorDataSource() {
        String url = System.getenv("MVP_POSTGRES_JDBC_URL");
        if (url == null || url.isBlank()) {
            url = "jdbc:postgresql://localhost:5432/manmu";
        }
        String user = System.getenv("MVP_POSTGRES_USER");
        if (user == null || user.isBlank()) {
            user = "manmu";
        }
        String password = System.getenv("MVP_POSTGRES_PASSWORD");
        if (password == null || password.isBlank()) {
            password = "manmu";
        }
        return DataSourceBuilder.create()
            .url(url)
            .username(user)
            .password(password)
            .driverClassName("org.postgresql.Driver")
            .build();
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource vectorDataSource) {
        return new JdbcTemplate(vectorDataSource);
    }

    @Bean
    PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .vectorTableName("rag_vectors")
            .dimensions(1536)
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .build();
    }

}
