package top.lanshan.manmu.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.lanshan.manmu.report.ReportResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@ConditionalOnProperty(prefix = "mvp.rag", name = "enabled", havingValue = "true")
public class RagDataController {

    private final VectorStoreDataIngestionService ingestionService;

    public RagDataController(VectorStoreDataIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ReportResponse<Map<String, Object>>>> upload(
            @RequestPart("file") org.springframework.http.codec.multipart.FilePart file,
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
        return file.content()
            .collectList()
            .map(dataBuffers -> {
                byte[] bytes = dataBuffers.stream()
                    .map(buffer -> {
                        byte[] b = new byte[buffer.readableByteCount()];
                        buffer.read(b);
                        return b;
                    })
                    .reduce(new byte[0], (a, b) -> {
                        byte[] result = new byte[a.length + b.length];
                        System.arraycopy(a, 0, result, 0, a.length);
                        System.arraycopy(b, 0, result, a.length, b.length);
                        return result;
                    });
                return new org.springframework.core.io.ByteArrayResource(bytes, file.filename());
            })
            .flatMap(resource -> Mono.fromCallable(() -> {
                int chunks = ingestionService.ingest(resource, finalSessionId, finalUserId);
                Map<String, Object> data = Map.of(
                    "file_name", file.filename(),
                    "chunks", chunks,
                    "session_id", finalSessionId);
                return ResponseEntity.ok(ReportResponse.success(finalSessionId,
                        "File ingested successfully", data));
            }).subscribeOn(Schedulers.boundedElastic()));
    }

}
