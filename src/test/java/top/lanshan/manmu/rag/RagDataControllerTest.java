package top.lanshan.manmu.rag;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.MultipartBodyBuilder;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RagDataControllerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class);

    @Test
    void ingestionServiceIsAvailableWhenRagEnabled() {
        contextRunner
            .withPropertyValues("mvp.rag.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(VectorStoreDataIngestionService.class));
    }

    @Test
    void uploadIngestsNonEmptyFileWithBoundSession() {
        RecordingIngestionService ingestionService = new RecordingIngestionService();
        StubRagDocumentRepository documentRepository = new StubRagDocumentRepository();
        WebTestClient client = WebTestClient.bindToController(new RagDataController(ingestionService, documentRepository)).build();

        client.post()
            .uri("/api/rag/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipart("notes.txt", "hello RAG".getBytes(), "session-1"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("success")
            .jsonPath("$.report_information.file_name")
            .isEqualTo("notes.txt")
            .jsonPath("$.report_information.chunks")
            .isEqualTo(1)
            .jsonPath("$.report_information.session_id")
            .isEqualTo("session-1");

        assertThat(ingestionService.contentLength).isEqualTo("hello RAG".getBytes().length);
        assertThat(ingestionService.sessionId).isEqualTo("session-1");
        assertThat(ingestionService.userId).isEqualTo("anonymous");
        assertThat(ingestionService.fileName).isEqualTo("notes.txt");
    }

    @Test
    void uploadRejectsEmptyFile() {
        RecordingIngestionService ingestionService = new RecordingIngestionService();
        StubRagDocumentRepository documentRepository = new StubRagDocumentRepository();
        WebTestClient client = WebTestClient.bindToController(new RagDataController(ingestionService, documentRepository)).build();

        client.post()
            .uri("/api/rag/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipart("empty.txt", new byte[0], "session-1"))
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("error")
            .jsonPath("$.message")
            .isEqualTo("上传文件不能为空");

        assertThat(ingestionService.calls).isZero();
    }

    @Test
    void uploadRejectsMissingFilePartWithClearError() {
        RecordingIngestionService ingestionService = new RecordingIngestionService();
        StubRagDocumentRepository documentRepository = new StubRagDocumentRepository();
        WebTestClient client = WebTestClient.bindToController(new RagDataController(ingestionService, documentRepository)).build();
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("session_id", "session-1");

        client.post()
            .uri("/api/rag/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(builder.build())
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("error")
            .jsonPath("$.message")
            .isEqualTo("上传文件不能为空");

        assertThat(ingestionService.calls).isZero();
    }

    @Test
    void uploadRejectsOversizedFile() {
        RecordingIngestionService ingestionService = new RecordingIngestionService();
        StubRagDocumentRepository documentRepository = new StubRagDocumentRepository();
        WebTestClient client = WebTestClient.bindToController(new RagDataController(ingestionService, documentRepository)).build();

        client.post()
            .uri("/api/rag/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipart("large.txt", new byte[RagDataController.MAX_UPLOAD_BYTES + 1], "session-1"))
            .exchange()
            .expectStatus()
            .isEqualTo(413)
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("error")
            .jsonPath("$.message")
            .isEqualTo("上传文件不能超过 10MB");

        assertThat(ingestionService.calls).isZero();
    }

    private static MultiValueMap<String, org.springframework.http.HttpEntity<?>> multipart(
            String filename, byte[] bytes, String sessionId) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", namedResource(filename, bytes));
        builder.part("session_id", sessionId);
        return builder.build();
    }

    private static ByteArrayResource namedResource(String filename, byte[] bytes) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    @Configuration
    static class TestConfig {
        @Bean
        VectorStoreDataIngestionService ingestionService() {
            return new VectorStoreDataIngestionService(new StubVectorStore());
        }
    }

    static class RecordingIngestionService extends VectorStoreDataIngestionService {
        int calls;
        String sessionId;
        String userId;
        String fileName;
        long contentLength;

        RecordingIngestionService() {
            super(new StubVectorStore());
        }

        @Override
        public int ingest(Resource resource, String sessionId, String userId) {
            this.calls++;
            this.sessionId = sessionId;
            this.userId = userId;
            this.fileName = resource.getFilename();
            try {
                this.contentLength = resource.contentLength();
            }
            catch (IOException e) {
                this.contentLength = -1;
            }
            return 1;
        }
    }

    static class StubVectorStore implements VectorStore {
        final List<Document> acceptedDocuments = new ArrayList<>();

        @Override
        public void add(List<Document> documents) {
            acceptedDocuments.addAll(documents);
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }

        @Override
        public List<Document> similaritySearch(String query) {
            return List.of();
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }
    }

    static class StubRagDocumentRepository implements RagDocumentRepository {
        final List<RagDocumentEntity> savedDocuments = new ArrayList<>();

        @Override
        public <S extends RagDocumentEntity> S save(S entity) {
            savedDocuments.add(entity);
            return entity;
        }

        @Override
        public <S extends RagDocumentEntity> Flux<S> saveAll(Iterable<S> entities) {
            return Flux.fromIterable(entities).doOnNext(this::save);
        }

        @Override
        public Mono<RagDocumentEntity> findById(UUID id) {
            return Mono.empty();
        }

        @Override
        public Mono<Boolean> existsById(UUID id) {
            return Mono.just(false);
        }

        @Override
        public Flux<RagDocumentEntity> findAll() {
            return Flux.fromIterable(savedDocuments);
        }

        @Override
        public Flux<RagDocumentEntity> findAllById(Iterable<UUID> ids) {
            return Flux.empty();
        }

        @Override
        public Mono<Long> count() {
            return Mono.just((long) savedDocuments.size());
        }

        @Override
        public Mono<Void> deleteById(UUID id) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteById(Mono<UUID> id) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> delete(RagDocumentEntity entity) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteAllById(Iterable<? extends UUID> ids) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteAll(Iterable<? extends RagDocumentEntity> entities) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteAll(Publisher<? extends RagDocumentEntity> entityStream) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteAll() {
            savedDocuments.clear();
            return Mono.empty();
        }

        @Override
        public <S extends RagDocumentEntity> Mono<S> saveAndFlush(S entity) {
            return save(entity);
        }

        @Override
        public Flux<RagDocumentEntity> findByScopeOrderByUploadedAtDesc(String scope) {
            return Flux.fromIterable(savedDocuments).filter(d -> scope.equals(d.getScope()));
        }

        @Override
        public Flux<RagDocumentEntity> findByScopeOrderByUploadedAtDesc(String scope, int limit) {
            return findByScopeOrderByUploadedAtDesc(scope).take(limit);
        }

        @Override
        public Mono<RagDocumentEntity> findByIdAndScope(UUID id, String scope) {
            return Mono.fromIterable(savedDocuments)
                .filter(d -> id.equals(d.getId()) && scope.equals(d.getScope()))
                .next();
        }

        @Override
        public Mono<Integer> deleteByIdAndScope(UUID id, String scope) {
            int before = savedDocuments.size();
            savedDocuments.removeIf(d -> id.equals(d.getId()) && scope.equals(d.getScope()));
            return Mono.just(before - savedDocuments.size());
        }
    }

}
