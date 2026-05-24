package top.lanshan.manmu.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.lanshan.manmu.modelprovider.ModelProviderKeyStore;

@Configuration
@ConditionalOnProperty(prefix = "mvp.rag", name = "enabled", havingValue = "true")
public class DashScopeEmbeddingConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DashScopeEmbeddingConfiguration.class);

    @Bean
    EmbeddingModel dashScopeEmbeddingModel(ModelProviderKeyStore keyStore) {
        String apiKey = keyStore.getApiKey("dashscope")
            .filter(k -> !k.isBlank())
            .orElse(null);
        if (apiKey == null) {
            apiKey = System.getenv("AI_DASHSCOPE_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "RAG embedding requires a DashScope API key. "
                + "Set it via .local/model-providers.json with key 'dashscope' "
                + "or via AI_DASHSCOPE_API_KEY environment variable.");
        }
        logger.info("Creating DashScope embedding model for RAG (model: text-embedding-v1)");
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(apiKey.strip()).build();
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
            .withModel("text-embedding-v1")
            .build();
        return new DashScopeEmbeddingModel(dashScopeApi, MetadataMode.EMBED, options);
    }

}
