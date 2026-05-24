package top.lanshan.manmu.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.config.RagProperties;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProfessionalKbDecisionNode implements ResearchNode {

    private static final Logger logger = LoggerFactory.getLogger(ProfessionalKbDecisionNode.class);

    private final ChatClient chatClient;

    private final RagProperties ragProperties;

    public ProfessionalKbDecisionNode(ChatClient.Builder chatClientBuilder, RagProperties ragProperties) {
        this.chatClient = chatClientBuilder.build();
        this.ragProperties = ragProperties;
    }

    @Override
    public int order() {
        return 32;
    }

    @Override
    public String name() {
        return "professional_kb_decision";
    }

    @Override
    public Flux<ResearchEvent> run(ResearchState state) {
        return Flux.defer(() -> {
            if (!ragProperties.getProfessionalKnowledgeBases().isDecisionEnabled()) {
                state.selectedKnowledgeBases(Collections.emptyList());
                return Flux.empty();
            }
            List<RagProperties.ProfessionalKnowledgeBases.KnowledgeBase> enabledKbs =
                    ragProperties.getProfessionalKnowledgeBases().getKnowledgeBases().stream()
                        .filter(RagProperties.ProfessionalKnowledgeBases.KnowledgeBase::isEnabled)
                        .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                        .toList();
            if (enabledKbs.isEmpty()) {
                state.selectedKnowledgeBases(Collections.emptyList());
                return Flux.empty();
            }
            String kbDescriptions = enabledKbs.stream()
                .map(kb -> "- ID: " + kb.getId() + ", Name: " + kb.getName()
                        + ", Description: " + kb.getDescription())
                .collect(Collectors.joining("\n"));
            String prompt = buildDecisionPrompt(state.query(), kbDescriptions);

            return Mono.fromCallable(() -> chatClient.prompt().user(prompt).call().content())
                .flatMapMany(response -> {
                    List<String> selectedIds = parseSelection(response, enabledKbs);
                    state.selectedKnowledgeBases(selectedIds);
                    logger.info("ProfessionalKbDecision selected KBs: {} for query: {}",
                            selectedIds, state.query().length() > 80
                                    ? state.query().substring(0, 80) + "..." : state.query());
                    return Flux.just(new ResearchEvent(state.threadId(), null, null,
                            name(), name(), null, null, null,
                            "decision", "decision", "专业知识库选择",
                            "Selected KBs: " + String.join(", ", selectedIds),
                            selectedIds, null, false, Instant.now()));
                });
        });
    }

    private String buildDecisionPrompt(String query, String kbDescriptions) {
        return """
            You are a research assistant deciding which knowledge bases to query.

            User Question: %s

            Available Knowledge Bases:
            %s

            Based on the user's question, select the knowledge bases that are MOST RELEVANT.
            Respond ONLY in this format:
            SELECTED: [kb_id1, kb_id2]
            or
            SELECTED: []
            """.formatted(query, kbDescriptions);
    }

    List<String> parseSelection(String response,
            List<RagProperties.ProfessionalKnowledgeBases.KnowledgeBase> enabledKbs) {
        List<String> validIds = enabledKbs.stream()
            .map(RagProperties.ProfessionalKnowledgeBases.KnowledgeBase::getId)
            .toList();
        int start = response.indexOf("SELECTED:");
        if (start < 0) {
            return Collections.emptyList();
        }
        String selection = response.substring(start + "SELECTED:".length()).trim();
        if (selection.startsWith("[") && selection.contains("]")) {
            String ids = selection.substring(selection.indexOf('[') + 1, selection.indexOf(']'));
            return java.util.Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(validIds::contains)
                .toList();
        }
        return Collections.emptyList();
    }

}
