package top.lanshan.manmu.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.eventhistory.ResearchEventHistoryService;
import top.lanshan.manmu.memory.ConversationMessageRecord;
import top.lanshan.manmu.memory.ConversationMessageRepository;
import top.lanshan.manmu.memory.ConversationMemoryService;
import top.lanshan.manmu.model.ApiResponse;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final Logger logger = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationMemoryService memoryService;
    private final ConversationMessageRepository repository;
    private final ResearchEventHistoryService eventHistoryService;

    public ConversationController(ConversationMemoryService memoryService,
            ConversationMessageRepository repository, ResearchEventHistoryService eventHistoryService) {
        this.memoryService = memoryService;
        this.repository = repository;
        this.eventHistoryService = eventHistoryService;
    }

    @GetMapping
    public Mono<ApiResponse<List<ConversationSummary>>> listConversations() {
        return repository.findDistinctSessionIds()
                .flatMap(this::buildSummary)
                .collectSortedList((a, b) -> b.lastMessageAt().compareTo(a.lastMessageAt()))
                .map(ApiResponse::success)
                .defaultIfEmpty(ApiResponse.success(List.of()));
    }

    @GetMapping("/{sessionId}")
    public Mono<ApiResponse<ConversationDetail>> getConversation(
            @PathVariable String sessionId) {
        return memoryService.findBySessionId(sessionId)
                .collectList()
                .map(messages -> {
                    String title = extractTitle(messages);
                    return new ConversationDetail(sessionId, title, messages.size(), messages);
                })
                .map(ApiResponse::success)
                .defaultIfEmpty(ApiResponse.success(
                        new ConversationDetail(sessionId, "Unnamed conversation", 0, List.of())));
    }

    @DeleteMapping("/{sessionId}")
    public Mono<ApiResponse<Void>> deleteConversation(@PathVariable String sessionId) {
        return eventHistoryService.deleteBySessionId(sessionId)
                .then(repository.deleteBySessionId(sessionId))
                .doOnSuccess(count -> logger.info("Deleted {} messages for session {}", count, sessionId))
                .then(Mono.just(ApiResponse.success(null)));
    }

    private Mono<ConversationSummary> buildSummary(String sessionId) {
        Mono<String> titleMono = repository.findFirstUserMessageBySessionId(sessionId)
                .map(m -> truncateTitle(m.getContent()))
                .defaultIfEmpty("Unnamed conversation");

        Mono<Long> countMono = repository.countBySessionId(sessionId).defaultIfEmpty(0L);
        Mono<java.time.Instant> lastAtMono = repository.findLatestMessageBySessionId(sessionId)
                .map(m -> m.getCreatedAt());

        return Mono.zip(titleMono, countMono, lastAtMono)
                .map(tuple -> new ConversationSummary(
                        sessionId, tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private String extractTitle(List<ConversationMessageRecord> messages) {
        return messages.stream()
                .filter(m -> "USER".equalsIgnoreCase(m.role()))
                .findFirst()
                .map(m -> truncateTitle(m.content()))
                .orElse("Unnamed conversation");
    }

    private static String truncateTitle(String content) {
        if (content == null || content.isBlank()) {
            return "Unnamed conversation";
        }
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }
}
