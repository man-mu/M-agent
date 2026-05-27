package top.lanshan.manmu.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.rag.RagRetriever;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ProfessionalKbRagNode implements ResearchNode {

    private static final Logger logger = LoggerFactory.getLogger(ProfessionalKbRagNode.class);

    private final RagRetriever retriever;

    private final ChatClient ragAgent;

    private final String ragPromptTemplate;

    public ProfessionalKbRagNode(RagRetriever retriever, ChatClient.Builder chatClientBuilder,
            ResourceLoader resourceLoader) {
        this.retriever = retriever;
        this.ragAgent = chatClientBuilder.build();
        this.ragPromptTemplate = loadPrompt(resourceLoader);
    }

    private String loadPrompt(ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource("classpath:prompts/rag.md");
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to load RAG prompt", e);
        }
    }

    @Override
    public int order() {
        return 33;
    }

    @Override
    public String name() {
        return "professional_kb_rag";
    }

    @Override
    public Flux<ResearchEvent> run(ResearchState state) {
        return Flux.defer(() -> {
            List<String> selectedKbs = state.selectedKnowledgeBases();
            if (selectedKbs == null || selectedKbs.isEmpty()) {
                return Flux.empty();
            }
            String query = state.query();
            Map<String, Object> filters = Map.of(
                "source_type", "professional_kb",
                "session_id", "professional_kb");
            List<Document> documents = retriever.retrieve(query, filters);
            if (documents.isEmpty()) {
                return Flux.just(new ResearchEvent(state.threadId(), null, null, name(), name(),
                        null, null, null, "completed", "completed",
                        "专业知识库检索", "No professional KB RAG context matched this query",
                        "未检索到可用的专业知识库上下文。", null, false, Instant.now()));
            }
            String context = retriever.buildContext(documents);
            String prompt = ragPromptTemplate
                .replace("{context}", context)
                .replace("{question}", query);

            return Mono.fromCallable(() -> ragAgent.prompt().user(prompt).call().content())
                .flatMapMany(ragContent -> {
                    state.addObservation("[KB-RAG] " + ragContent);
                    return Flux.just(new ResearchEvent(state.threadId(), null, null, name(), name(),
                            null, null, null, "completed", "completed",
                            "专业知识库检索", "Professional KB RAG context applied", ragContent,
                            null, false, Instant.now()));
                });
        });
    }

}
