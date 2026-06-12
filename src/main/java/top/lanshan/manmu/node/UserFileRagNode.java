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
import top.lanshan.manmu.modelprovider.ModelProviderRegistry;
import top.lanshan.manmu.rag.RagRetriever;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public class UserFileRagNode implements ResearchNode {

	private static final Logger logger = LoggerFactory.getLogger(UserFileRagNode.class);

	private static final String DISPLAY_TITLE = "读取上传资料";

	private static final String NO_CONTEXT_PAYLOAD = "No user-upload RAG context was retrieved.";

	private final RagRetriever retriever;

	private final ModelProviderRegistry modelProviderRegistry;

	private final String ragPromptTemplate;

	public UserFileRagNode(RagRetriever retriever, ModelProviderRegistry modelProviderRegistry, ResourceLoader resourceLoader) {
		this.retriever = retriever;
		this.modelProviderRegistry = modelProviderRegistry;
		this.ragPromptTemplate = loadPrompt(resourceLoader);
	}

	private String loadPrompt(ResourceLoader resourceLoader) {
		Resource resource = resourceLoader.getResource("classpath:prompts/rag.md");
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to load RAG prompt from classpath:prompts/rag.md", e);
		}
	}

	@Override
	public int order() {
		return 8;
	}

	@Override
	public String name() {
		return "user_file_rag";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			String sessionId = state.sessionId();
			if (sessionId == null || sessionId.isBlank()) {
				return Flux.empty();
			}
			String query = queryForRag(state);
			ResearchEvent started = event(state, "started", "started", "Retrieving user-upload RAG context", query);
			return Flux.concat(Flux.just(started), retrieveAndApplyRag(state, sessionId, query));
		});
	}

	private Flux<ResearchEvent> retrieveAndApplyRag(ResearchState state, String sessionId, String query) {
		return Flux.defer(() -> {
			logger.info("UserFileRagNode retrieving for session={}, query={}", sessionId,
					query.length() > 80 ? query.substring(0, 80) + "..." : query);

			List<Document> documents = retriever.retrieveWithGlobal(query, sessionId);
			if (documents.isEmpty()) {
				return Flux.just(event(state, "completed", "completed",
						"No user-upload RAG context matched this query", NO_CONTEXT_PAYLOAD));
			}

			String context = retriever.buildContext(documents);
			String prompt = ragPromptTemplate.replace("{context}", context).replace("{question}", query);

			// 每次调用时获取最新的 ChatClient，支持动态切换模型
			ChatClient ragAgent = modelProviderRegistry.getChatClientBuilder().build();

			return Mono.fromCallable(() -> ragAgent.prompt().user(prompt).call().content()).flatMapMany(ragContent -> {
				state.addObservation("[RAG] " + ragContent);
				return Flux.just(event(state, "completed", "completed", "RAG context retrieved and applied",
						ragContent));
			});
		});
	}

	private ResearchEvent event(ResearchState state, String phase, String status, String content, Object payload) {
		return new ResearchEvent(state.threadId(), null, null, name(), name(), null, null, null, phase, status,
				DISPLAY_TITLE, content, payload, null, false, null, Instant.now());
	}

	private String queryForRag(ResearchState state) {
		if (state.optimizedQueries() != null && !state.optimizedQueries().isEmpty()) {
			return String.join(" ", state.optimizedQueries());
		}
		return state.query();
	}

}
