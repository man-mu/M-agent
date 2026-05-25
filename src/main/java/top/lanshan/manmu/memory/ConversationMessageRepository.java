package top.lanshan.manmu.memory;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ConversationMessageRepository extends ReactiveCrudRepository<ConversationMessageEntity, UUID> {

    Flux<ConversationMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
