package top.lanshan.manmu.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PostgresConversationMemoryService implements ConversationMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(PostgresConversationMemoryService.class);

    private final ConversationMessageRepository repository;

    public PostgresConversationMemoryService(ConversationMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<ConversationMessageRecord> saveMessage(String sessionId, String threadId,
            String role, String content) {
        Instant now = Instant.now();
        ConversationMessageEntity entity = newEntity(sessionId, threadId, role, content, now);
        return repository.save(entity)
                .map(ConversationMessageEntity::toRecord);
    }

    @Override
    public Flux<ConversationMessageRecord> findBySessionId(String sessionId) {
        return repository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .takeLast(20)
                .map(ConversationMessageEntity::toRecord);
    }

    @Override
    public Mono<String> formatConversationHistory(String sessionId) {
        return findBySessionId(sessionId).collectList().map(messages -> {
            if (messages.isEmpty()) {
                return "";
            }
            return messages.stream()
                    .map(m -> m.role() + ": " + truncate(m.content(), 800))
                    .collect(Collectors.joining("\n",
                            "Previous conversation in this session:\n", ""));
        });
    }

    private String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "...[truncated]";
    }

    private ConversationMessageEntity newEntity(String sessionId, String threadId,
            String role, String content, Instant createdAt) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setNewMessage(true);
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setThreadId(threadId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
