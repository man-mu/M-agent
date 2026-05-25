package top.lanshan.manmu.memory;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserProfileRepository extends ReactiveCrudRepository<UserProfileEntity, UUID> {

    Mono<UserProfileEntity> findTopBySessionIdOrderByUpdatedAtDesc(String sessionId);
}
