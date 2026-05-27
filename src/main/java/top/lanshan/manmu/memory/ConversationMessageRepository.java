package top.lanshan.manmu.memory;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConversationMessageRepository extends ReactiveCrudRepository<ConversationMessageEntity, UUID> {

    Flux<ConversationMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    @Query("SELECT DISTINCT session_id FROM conversation_messages")
    Flux<String> findDistinctSessionIds();

    @Query("SELECT COUNT(*) FROM conversation_messages WHERE session_id = $1")
    Mono<Long> countBySessionId(String sessionId);

    @Query("SELECT * FROM conversation_messages WHERE session_id = $1 ORDER BY created_at DESC LIMIT 1")
    Mono<ConversationMessageEntity> findLatestMessageBySessionId(String sessionId);

    @Query("SELECT * FROM conversation_messages WHERE session_id = $1 AND role = 'USER' ORDER BY created_at ASC LIMIT 1")
    Mono<ConversationMessageEntity> findFirstUserMessageBySessionId(String sessionId);

    @Modifying
    @Query("DELETE FROM conversation_messages WHERE session_id = $1")
    Mono<Integer> deleteBySessionId(String sessionId);
}
