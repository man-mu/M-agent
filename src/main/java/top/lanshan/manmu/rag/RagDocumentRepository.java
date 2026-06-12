package top.lanshan.manmu.rag;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RagDocumentRepository extends ReactiveCrudRepository<RagDocumentEntity, UUID> {

    Flux<RagDocumentEntity> findByScopeOrderByUploadedAtDesc(String scope);

    @Query("SELECT * FROM rag_documents WHERE scope = $1 ORDER BY uploaded_at DESC LIMIT $2")
    Flux<RagDocumentEntity> findByScopeOrderByUploadedAtDesc(String scope, int limit);

    Mono<RagDocumentEntity> findByIdAndScope(UUID id, String scope);

    @Modifying
    @Query("DELETE FROM rag_documents WHERE id = $1 AND scope = $2")
    Mono<Integer> deleteByIdAndScope(UUID id, String scope);
}
