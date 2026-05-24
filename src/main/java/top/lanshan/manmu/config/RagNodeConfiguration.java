package top.lanshan.manmu.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import top.lanshan.manmu.modelprovider.ModelProviderRegistry;
import top.lanshan.manmu.node.ProfessionalKbDecisionNode;
import top.lanshan.manmu.node.ProfessionalKbRagNode;
import top.lanshan.manmu.node.UserFileRagNode;
import top.lanshan.manmu.rag.RagRetriever;
import top.lanshan.manmu.rag.VectorStoreDataIngestionService;

@Configuration
@ConditionalOnProperty(prefix = "mvp.rag", name = "enabled", havingValue = "true")
public class RagNodeConfiguration {

    @Bean
    VectorStoreDataIngestionService vectorStoreDataIngestionService(VectorStore vectorStore) {
        return new VectorStoreDataIngestionService(vectorStore);
    }

    @Bean
    RagRetriever ragRetriever(VectorStore vectorStore, RagProperties ragProperties) {
        return new RagRetriever(vectorStore, ragProperties.getTopK(),
                ragProperties.getSimilarityThreshold());
    }

    @Bean
    UserFileRagNode userFileRagNode(RagRetriever ragRetriever,
            ModelProviderRegistry modelProviderRegistry) {
        return new UserFileRagNode(ragRetriever,
                modelProviderRegistry.getChatClientBuilder(),
                new DefaultResourceLoader());
    }

    @Bean
    ProfessionalKbDecisionNode professionalKbDecisionNode(
            ModelProviderRegistry modelProviderRegistry, RagProperties ragProperties) {
        return new ProfessionalKbDecisionNode(
                modelProviderRegistry.getChatClientBuilder(), ragProperties);
    }

    @Bean
    ProfessionalKbRagNode professionalKbRagNode(RagRetriever ragRetriever,
            ModelProviderRegistry modelProviderRegistry) {
        return new ProfessionalKbRagNode(ragRetriever,
                modelProviderRegistry.getChatClientBuilder(),
                new DefaultResourceLoader());
    }

}
