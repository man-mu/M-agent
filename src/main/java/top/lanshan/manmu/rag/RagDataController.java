package top.lanshan.manmu.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.lanshan.manmu.report.ReportResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@ConditionalOnProperty(prefix = "mvp.rag", name = "enabled", havingValue = "true")
public class RagDataController {

    static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

    private final VectorStoreDataIngestionService ingestionService;

    public RagDataController(VectorStoreDataIngestionService ingestionService) {
        this.ingestionService = ingestionService;
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
            @RequestPart(value = "user_id", required = false) String userId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "__default__";
        }
        if (userId == null || userId.isBlank()) {
            userId = "anonymous";
        }
        String finalSessionId = sessionId;
        String finalUserId = userId;
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
                    int chunks = ingestionService.ingest(resource, finalSessionId, finalUserId);
                    Map<String, Object> data = Map.of(
                        "file_name", file.filename(),
                        "chunks", chunks,
                        "session_id", finalSessionId);
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

}
