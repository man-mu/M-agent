package top.lanshan.manmu.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.lanshan.manmu.report.ReportResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag")
@ConditionalOnProperty(prefix = "mvp.rag", name = "enabled", havingValue = "true")
public class RagDataController {

    static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    static final int MAX_LIST_LIMIT = 50;

    private final VectorStoreDataIngestionService ingestionService;
    private final RagDocumentRepository ragDocumentRepository;

    public RagDataController(VectorStoreDataIngestionService ingestionService,
            RagDocumentRepository ragDocumentRepository) {
        this.ingestionService = ingestionService;
        this.ragDocumentRepository = ragDocumentRepository;
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ReportResponse<Map<String, Object>>> handleUploadInputError(ServerWebInputException error) {
        return ResponseEntity.badRequest()
            .body(ReportResponse.error("__default__", "上传文件不能为空"));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ReportResponse<Map<String, Object>>>> upload(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "session_id", required = false) String sessionId,
            @RequestPart(value = "user_id", required = false) String userId,
            @RequestParam(value = "scope", defaultValue = "session") String scope) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "__default__";
        }
        if (userId == null || userId.isBlank()) {
            userId = "anonymous";
        }
        String finalSessionId = sessionId;
        String finalUserId = userId;
        String finalScope = scope.isBlank() ? "session" : scope;
        return DataBufferUtils.join(file.content(), MAX_UPLOAD_BYTES)
            .flatMap(buffer -> Mono.fromCallable(() -> {
                try {
                    int readableBytes = buffer.readableByteCount();
                    if (readableBytes <= 0) {
                        return ResponseEntity.badRequest()
                            .body(ReportResponse.<Map<String, Object>>error(finalSessionId,
                                    "上传文件不能为空"));
                    }
                    byte[] bytes = new byte[readableBytes];
                    buffer.read(bytes);
                    ByteArrayResource resource = new ByteArrayResource(bytes) {
                        @Override
                        public String getFilename() {
                            return file.filename();
                        }
                    };
                    int chunks = ingestionService.ingest(resource, finalSessionId, finalUserId, finalScope);

                    // 写入文档元数据表
                    RagDocumentEntity doc = new RagDocumentEntity();
                    doc.setNewEntity(true);
                    doc.setId(UUID.randomUUID());
                    doc.setFileName(file.filename());
                    doc.setScope(finalScope);
                    doc.setSessionId(finalSessionId);
                    doc.setUserId(finalUserId);
                    doc.setChunks(chunks);
                    doc.setUploadedAt(Instant.now());
                    ragDocumentRepository.save(doc).block();

                    Map<String, Object> data = Map.of(
                        "file_name", file.filename(),
                        "chunks", chunks,
                        "session_id", finalSessionId,
                        "scope", finalScope);
                    return ResponseEntity.ok(ReportResponse.success(finalSessionId,
                            "File ingested successfully", data));
                }
                finally {
                    DataBufferUtils.release(buffer);
                }
            }).subscribeOn(Schedulers.boundedElastic()))
            .switchIfEmpty(Mono.just(ResponseEntity.badRequest()
                .body(ReportResponse.<Map<String, Object>>error(finalSessionId, "上传文件不能为空"))))
            .onErrorResume(DataBufferLimitException.class, e -> Mono.just(ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ReportResponse.<Map<String, Object>>error(finalSessionId,
                        "上传文件不能超过 10MB"))));
    }

    @GetMapping("/documents")
    public Mono<ResponseEntity<ReportResponse<Map<String, Object>>>> listDocuments(
            @RequestParam(value = "scope", defaultValue = "global") String scope,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        return ragDocumentRepository.findByScopeOrderByUploadedAtDesc(scope, safeLimit)
            .collectList()
            .map(docs -> {
                List<Map<String, Object>> items = docs.stream()
                    .map(doc -> Map.<String, Object>of(
                        "id", doc.getId().toString(),
                        "fileName", doc.getFileName(),
                        "chunks", doc.getChunks(),
                        "uploadedAt", doc.getUploadedAt() != null ? doc.getUploadedAt().toString() : ""))
                    .toList();
                Map<String, Object> data = Map.of("documents", items);
                return ResponseEntity.ok(ReportResponse.success("__global__", "OK", data));
            });
    }

    @DeleteMapping("/documents/{id}")
    public Mono<ResponseEntity<ReportResponse<Map<String, Object>>>> deleteDocument(
            @PathVariable("id") String id,
            @RequestParam(value = "scope", defaultValue = "global") String scope) {
        UUID docId;
        try {
            docId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest()
                .body(ReportResponse.<Map<String, Object>>error("__global__", "无效的文档 ID")));
        }
        return ragDocumentRepository.findByIdAndScope(docId, scope)
            .flatMap(existing -> ragDocumentRepository.deleteByIdAndScope(docId, scope)
                .map(deleted -> {
                    Map<String, Object> data = Map.of("deleted", deleted > 0, "id", id);
                    return ResponseEntity.ok(ReportResponse.success("__global__", "OK", data));
                }))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ReportResponse.<Map<String, Object>>error("__global__", "文档不存在")));
    }
}
