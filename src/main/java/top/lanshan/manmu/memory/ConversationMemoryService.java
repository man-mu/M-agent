package top.lanshan.manmu.memory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConversationMemoryService {

    Mono<ConversationMessageRecord> saveMessage(String sessionId, String threadId,
            String role, String content);

    Flux<ConversationMessageRecord> findBySessionId(String sessionId);

    Mono<String> formatConversationHistory(String sessionId);
}
