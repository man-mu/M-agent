# 全局知识库 & 用户画像 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增全局知识库页面，支持全局文档上传（所有会话共享 RAG 检索）和用户画像展示/手动覆盖。

**Architecture:** 使用 scope 标记区分全局/会话级文档和画像，复用现有 pgvector 向量存储和 R2DBC 用户画像表，新增 `rag_documents` 元数据表记录上传历史。前端新增 `/knowledge` 路由页面。

**Tech Stack:** Spring WebFlux, R2DBC, pgvector, Spring AI VectorStore, Vue 3, Ant Design Vue, TypeScript

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `src/main/resources/db/migration/V9__add_global_knowledge_and_profile_manual_fields.sql` | PostgreSQL 迁移：rag_documents 表 + user_profiles 新列 |
| `src/main/resources/db/h2-migration/V9__add_global_knowledge_and_profile_manual_fields.sql` | H2 迁移（兼容语法） |
| `src/main/java/top/lanshan/manmu/rag/RagDocumentEntity.java` | R2DBC 实体：rag_documents 表映射 |
| `src/main/java/top/lanshan/manmu/rag/RagDocumentRepository.java` | R2DBC Repository：文档元数据 CRUD |
| `src/main/java/top/lanshan/manmu/api/UserProfileController.java` | REST API：全局画像 GET/PUT/reset |
| `ui-vue3/src/views/knowledge/index.vue` | 知识库页面组件 |
| `ui-vue3/src/services/api/knowledge.ts` | 前端 API 服务 |

### Modified Files

| File | Changes |
|------|---------|
| `src/main/java/top/lanshan/manmu/rag/VectorStoreDataIngestionService.java` | ingest 方法新增 scope 参数，写入 metadata |
| `src/main/java/top/lanshan/manmu/rag/RagDataController.java` | 新增 scope 参数、文档列表接口、删除接口 |
| `src/main/java/top/lanshan/manmu/rag/RagRetriever.java` | 新增 retrieveWithGlobal 方法合并全局+会话文档 |
| `src/main/java/top/lanshan/manmu/memory/UserProfileEntity.java` | 新增 scope、manualFields 字段 |
| `src/main/java/top/lanshan/manmu/memory/UserProfileRepository.java` | 新增 findByScope 查询 |
| `src/main/java/top/lanshan/manmu/memory/UserProfileService.java` | 自动提取时跳过 manual_fields 标记的字段 |
| `ui-vue3/src/components/layout/index.vue` | 导航栏新增知识库标签 |
| `ui-vue3/src/router/defaultRoutes.ts` | 新增 /knowledge 路由 |
| `src/main/java/top/lanshan/manmu/node/UserFileRagNode.java` | 使用 retrieveWithGlobal 替代 retrieve |

---

### Task 1: Flyway 迁移 — rag_documents 表 + user_profiles 新列

**Files:**
- Create: `src/main/resources/db/migration/V9__add_global_knowledge_and_profile_manual_fields.sql`
- Create: `src/main/resources/db/h2-migration/V9__add_global_knowledge_and_profile_manual_fields.sql`

- [ ] **Step 1: 创建 PostgreSQL 迁移文件**

```sql
-- src/main/resources/db/migration/V9__add_global_knowledge_and_profile_manual_fields.sql

-- 全局文档元数据表
CREATE TABLE IF NOT EXISTS rag_documents (
    id UUID PRIMARY KEY,
    file_name VARCHAR(500) NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'session',
    session_id VARCHAR(100),
    user_id VARCHAR(100),
    chunks INT NOT NULL DEFAULT 0,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rag_documents_scope ON rag_documents(scope);
CREATE INDEX IF NOT EXISTS idx_rag_documents_session ON rag_documents(session_id);

-- 用户画像新增列
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'session';
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS manual_fields TEXT NOT NULL DEFAULT '[]';
CREATE INDEX IF NOT EXISTS idx_user_profiles_scope ON user_profiles(scope);
```

- [ ] **Step 2: 创建 H2 迁移文件**

```sql
-- src/main/resources/db/h2-migration/V9__add_global_knowledge_and_profile_manual_fields.sql

-- 全局文档元数据表
CREATE TABLE IF NOT EXISTS rag_documents (
    id UUID PRIMARY KEY,
    file_name VARCHAR(500) NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'session',
    session_id VARCHAR(100),
    user_id VARCHAR(100),
    chunks INT NOT NULL DEFAULT 0,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_documents_scope ON rag_documents(scope);
CREATE INDEX IF NOT EXISTS idx_rag_documents_session ON rag_documents(session_id);

-- 用户画像新增列
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'session';
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS manual_fields TEXT NOT NULL DEFAULT '[]';
CREATE INDEX IF NOT EXISTS idx_user_profiles_scope ON user_profiles(scope);
```

- [ ] **Step 3: 验证迁移语法**

Run: `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; mvn '-Dtest=FlywayMigrationTest' compile -pl . -q`（如果存在 Flyway 测试）或直接 `mvn compile` 确认无编译错误。

- [ ] **Step 4: 提交**

```bash
git add src/main/resources/db/migration/V9__*.sql src/main/resources/db/h2-migration/V9__*.sql
git commit -m "feat: V9 迁移 — rag_documents 表和 user_profiles scope/manual_fields 列"
```

---

### Task 2: RagDocument 实体 + Repository

**Files:**
- Create: `src/main/java/top/lanshan/manmu/rag/RagDocumentEntity.java`
- Create: `src/main/java/top/lanshan/manmu/rag/RagDocumentRepository.java`

- [ ] **Step 1: 创建 RagDocumentEntity**

```java
// src/main/java/top/lanshan/manmu/rag/RagDocumentEntity.java
package top.lanshan.manmu.rag;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("rag_documents")
public class RagDocumentEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean newEntity;

    @Column("file_name")
    private String fileName;

    @Column("scope")
    private String scope;

    @Column("session_id")
    private String sessionId;

    @Column("user_id")
    private String userId;

    @Column("chunks")
    private int chunks;

    @Column("uploaded_at")
    private Instant uploadedAt;

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return newEntity; }

    public void setId(UUID id) { this.id = id; }
    public void setNewEntity(boolean newEntity) { this.newEntity = newEntity; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public int getChunks() { return chunks; }
    public void setChunks(int chunks) { this.chunks = chunks; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
}
```

- [ ] **Step 2: 创建 RagDocumentRepository**

```java
// src/main/java/top/lanshan/manmu/rag/RagDocumentRepository.java
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
```

- [ ] **Step 3: 提交**

```bash
git add src/main/java/top/lanshan/manmu/rag/RagDocumentEntity.java src/main/java/top/lanshan/manmu/rag/RagDocumentRepository.java
git commit -m "feat: RagDocument 实体和 Repository"
```

---

### Task 3: VectorStoreDataIngestionService — 支持 scope 参数

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/rag/VectorStoreDataIngestionService.java:32-78`

- [ ] **Step 1: 修改 ingest 方法签名，新增 scope 参数**

在 `VectorStoreDataIngestionService.java` 中：

1. 将 `ingest(Resource file, String sessionId, String userId)` 改为 `ingest(Resource file, String sessionId, String userId, String scope)`
2. 在 metadata Map 中新增 `"scope", scope` 和 `"source_type", "user_upload"` 条目
3. 新增一个兼容旧签名的重载方法

```java
// 修改后的 ingest 方法（替换原 ingest 方法）
public int ingest(Resource file, String sessionId, String userId, String scope) {
    try (InputStream is = file.getInputStream()) {
        byte[] allBytes = is.readAllBytes();
        if (allBytes.length == 0) {
            return 0;
        }

        String text = detectAndExtract(file.getFilename(), allBytes);
        if (text == null || text.isBlank()) {
            return 0;
        }

        String normalized = normalizeWhitespace(text);
        String contentToEmbed = normalized.length() > 30000 ? normalized.substring(0, 30000) : normalized;

        TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(3000)
            .build();

        List<Document> chunks = splitter.apply(List.of(new Document(contentToEmbed)));

        String fileName = file.getFilename();
        String safeName = fileName == null ? "unknown" : fileName;
        String safeSessionId = sessionId == null ? "__default__" : sessionId;
        String safeUserId = userId == null ? "anonymous" : userId;
        String safeScope = scope == null ? "session" : scope;
        Instant now = Instant.now();

        List<Document> enriched = chunks.stream()
            .filter(c -> c.getText() != null && !c.getText().isBlank())
            .map(c -> {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("session_id", safeSessionId);
                meta.put("user_id", safeUserId);
                meta.put("original_filename", safeName);
                meta.put("source_type", "user_upload");
                meta.put("scope", safeScope);
                meta.put("ingested_at", now.toString());
                return new Document(c.getText(), meta);
            })
            .toList();

        if (enriched.isEmpty()) {
            return 0;
        }

        vectorStore.add(enriched);
        return enriched.size();
    }
    catch (Exception e) {
        throw new IllegalStateException("Failed to ingest file: " + file.getFilename(), e);
    }
}

// 兼容旧签名的重载方法
public int ingest(Resource file, String sessionId, String userId) {
    return ingest(file, sessionId, userId, "session");
}
```

- [ ] **Step 2: 验证编译**

Run: `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; mvn compile -q`

Expected: 编译成功，无错误

- [ ] **Step 3: 提交**

```bash
git add src/main/java/top/lanshan/manmu/rag/VectorStoreDataIngestionService.java
git commit -m "feat: VectorStoreDataIngestionService 支持 scope 参数区分全局/会话级文档"
```

---

### Task 4: RagDataController — 全局文档上传、列表、删除

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/rag/RagDataController.java`

- [ ] **Step 1: 注入 RagDocumentRepository，修改 upload 方法**

在 `RagDataController.java` 中：

1. 注入 `RagDocumentRepository`
2. 修改 upload 方法：接收 `scope` 参数，传递给 ingestionService，写入 `rag_documents` 元数据表
3. 新增 `listDocuments(scope)` GET 端点
4. 新增 `deleteDocument(id, scope)` DELETE 端点

```java
// 修改后的 RagDataController.java 完整内容
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
```

- [ ] **Step 2: 验证编译**

Run: `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; mvn compile -q`

Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/top/lanshan/manmu/rag/RagDataController.java
git commit -m "feat: RagDataController 支持全局文档上传、列表和删除"
```

---

### Task 5: RagRetriever — 合并全局 + 会话级文档检索

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/rag/RagRetriever.java`

- [ ] **Step 1: 新增 retrieveWithGlobal 方法**

在 `RagRetriever.java` 中新增方法，先查全局文档，再查会话文档，合并去重：

```java
// 新增方法，添加到 RagRetriever 类中
public List<Document> retrieveWithGlobal(String query, String sessionId) {
    // 查询全局文档
    Map<String, Object> globalFilters = Map.of("scope", "global");
    List<Document> globalDocs = retrieve(query, globalFilters);

    // 查询会话级文档
    List<Document> sessionDocs = List.of();
    if (sessionId != null && !sessionId.isBlank()) {
        Map<String, Object> sessionFilters = Map.of("scope", "session", "session_id", sessionId);
        sessionDocs = retrieve(query, sessionFilters);
    }

    // 合并去重（按文本内容去重），全局文档优先
    Map<String, Document> merged = new LinkedHashMap<>();
    for (Document doc : globalDocs) {
        merged.putIfAbsent(doc.getText(), doc);
    }
    for (Document doc : sessionDocs) {
        merged.putIfAbsent(doc.getText(), doc);
    }

    List<Document> result = merged.values().stream().toList();
    logger.info("retrieveWithGlobal: global={}, session={}, merged={} for query: {}",
            globalDocs.size(), sessionDocs.size(), result.size(),
            query.length() > 80 ? query.substring(0, 80) + "..." : query);
    return result;
}
```

需要在文件顶部添加 `import java.util.LinkedHashMap;`。

- [ ] **Step 2: 验证编译**

Run: `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; mvn compile -q`

Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/top/lanshan/manmu/rag/RagRetriever.java
git commit -m "feat: RagRetriever 新增 retrieveWithGlobal 合并全局和会话级文档"
```

---

### Task 6: UserFileRagNode — 使用 retrieveWithGlobal

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/node/UserFileRagNode.java:74-95`

- [ ] **Step 1: 修改 retrieveAndApplyRag 方法**

将 `retriever.retrieve(query, filters)` 替换为 `retriever.retrieveWithGlobal(query, sessionId)`：

```java
// 修改后的 retrieveAndApplyRag 方法（替换原方法）
private Flux<ResearchEvent> retrieveAndApplyRag(ResearchState state, String sessionId, String query) {
    return Flux.defer(() -> {
        logger.info("UserFileRagNode retrieving for session={}, query={}", sessionId,
                query.length() > 80 ? query.substring(0, 80) + "..." : query);

        List<Document> documents = retriever.retrieveWithGlobal(query, sessionId);
        if (documents.isEmpty()) {
            return Flux.just(event(state, "completed", "completed",
                    "No user-upload RAG context matched this query", NO_CONTEXT_PAYLOAD));
        }

        String context = retriever.buildContext(documents);
        String prompt = ragPromptTemplate.replace("{context}", context).replace("{question}", query);

        return Mono.fromCallable(() -> ragAgent.prompt().user(prompt).call().content()).flatMapMany(ragContent -> {
            state.addObservation("[RAG] " + ragContent);
            return Flux.just(event(state, "completed", "completed", "RAG context retrieved and applied",
                    ragContent));
        });
    });
}
```

- [ ] **Step 2: 验证编译**

Run: `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; mvn compile -q`

Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/top/lanshan/manmu/node/UserFileRagNode.java
git commit -m "feat: UserFileRagNode 使用 retrieveWithGlobal 合并全局文档"
```

---

### Task 7: UserProfileEntity — 新增 scope + manualFields 字段

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/memory/UserProfileEntity.java`

- [ ] **Step 1: 新增字段和 getter/setter**

在 `UserProfileEntity.java` 中新增：

```java
// 在现有字段之后添加
@Column("scope")
private String scope = "session";

@Column("manual_fields")
private String manualFields = "[]";

// getter/setter
public String getScope() { return scope; }
public void setScope(String scope) { this.scope = scope; }
public String getManualFields() { return manualFields; }
public void setNewEntity(boolean manualFields) { this.manualFields = manualFields; }
public void setManualFields(String manualFields) { this.manualFields = manualFields; }
```

注意：需要修正 setter 名称冲突。`setNewEntity` 已存在用于 `newEntity` 字段，新增的 manualFields setter 用 `setManualFields` 即可，无需额外修改。

- [ ] **Step 2: 更新 toRecord 方法**

在 `toRecord()` 方法中不需要包含 scope 和 manualFields（Record 只用于 API 返回画像快照）。保持不变。

- [ ] **Step 3: 验证编译**

Run: `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; mvn compile -q`

Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add src/main/java/top/lanshan/manmu/memory/UserProfileEntity.java
git commit -m "feat: UserProfileEntity 新增 scope 和 manualFields 字段"
```

---

### Task 8: UserProfileRepository + UserProfileService — 全局画像 + 手动覆盖保护

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/memory/UserProfileRepository.java`
- Modify: `src/main/java/top/lanshan/manmu/memory/UserProfileService.java`

- [ ] **Step 1: UserProfileRepository 新增查询**

```java
// 在 UserProfileRepository.java 中新增
Mono<UserProfileEntity> findTopByScopeOrderByUpdatedAtDesc(String scope);
```

- [ ] **Step 2: UserProfileService 新增全局画像方法**

在 `UserProfileService.java` 中新增以下方法：

```java
// 新增常量
private static final String GLOBAL_SCOPE = "global";
private static final String GLOBAL_SESSION_ID = "__global__";

// 获取全局画像
public UserProfileEntity getGlobalProfile() {
    return profileRepository.findTopByScopeOrderByUpdatedAtDesc(GLOBAL_SCOPE).block();
}

// 更新全局画像（手动覆盖）
public UserProfileEntity updateGlobalProfile(String profileSummary, String expertiseLevel,
        String detailPreference, String stylePreference, List<String> manualFields) {
    UserProfileEntity entity = profileRepository.findTopByScopeOrderByUpdatedAtDesc(GLOBAL_SCOPE).block();
    if (entity == null) {
        entity = new UserProfileEntity();
        entity.setNewEntity(true);
        entity.setId(UUID.randomUUID());
        entity.setSessionId(GLOBAL_SESSION_ID);
        entity.setScope(GLOBAL_SCOPE);
    }
    if (profileSummary != null) entity.setProfileSummary(profileSummary);
    if (expertiseLevel != null) entity.setExpertiseLevel(expertiseLevel);
    if (detailPreference != null) entity.setDetailPreference(detailPreference);
    if (stylePreference != null) entity.setStylePreference(stylePreference);
    entity.setManualFields(objectMapper.writeValueAsString(manualFields != null ? manualFields : List.of()));
    entity.setUpdatedAt(Instant.now());
    return profileRepository.save(entity).block();
}

// 重置手动覆盖
public UserProfileEntity resetGlobalManualFields() {
    UserProfileEntity entity = profileRepository.findTopByScopeOrderByUpdatedAtDesc(GLOBAL_SCOPE).block();
    if (entity == null) {
        return null;
    }
    entity.setManualFields("[]");
    entity.setUpdatedAt(Instant.now());
    return profileRepository.save(entity).block();
}
```

需要在文件顶部添加 `import java.util.List;`。

- [ ] **Step 3: 修改自动提取逻辑 — 跳过手动覆盖字段**

在 `UserProfileService.java` 的 `fillEntity` 方法中，读取 `manualFields` 并跳过已标记的字段：

```java
// 修改 fillEntity 方法
private void fillEntity(UserProfileEntity entity, UserProfileFields fields) {
    Set<String> manual = parseManualFields(entity.getManualFields());
    if (!manual.contains("profile_summary")) {
        entity.setProfileSummary(fields.profileSummary());
    }
    if (!manual.contains("expertise_level")) {
        entity.setExpertiseLevel(fields.expertiseLevel());
    }
    if (!manual.contains("detail_preference")) {
        entity.setDetailPreference(fields.detailPreference());
    }
    if (!manual.contains("style_preference")) {
        entity.setStylePreference(fields.stylePreference());
    }
}

// 新增辅助方法
private Set<String> parseManualFields(String json) {
    if (json == null || json.isBlank() || "[]".equals(json.trim())) {
        return Set.of();
    }
    try {
        return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {})
            .stream().collect(Collectors.toSet());
    } catch (Exception e) {
        return Set.of();
    }
}
```

需要在文件顶部添加 `import com.fasterxml.jackson.core.type.TypeReference;`。

- [ ] **Step 4: 验证编译**

Run: `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; mvn compile -q`

Expected: 编译成功

- [ ] **Step 5: 提交**

```bash
git add src/main/java/top/lanshan/manmu/memory/UserProfileRepository.java src/main/java/top/lanshan/manmu/memory/UserProfileService.java
git commit -m "feat: 全局画像支持和手动覆盖保护逻辑"
```

---

### Task 9: UserProfileController — 全局画像 API

**Files:**
- Create: `src/main/java/top/lanshan/manmu/api/UserProfileController.java`

- [ ] **Step 1: 创建控制器**

```java
// src/main/java/top/lanshan/manmu/api/UserProfileController.java
package top.lanshan.manmu.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.memory.UserProfileEntity;
import top.lanshan.manmu.memory.UserProfileService;
import top.lanshan.manmu.report.ReportResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public Mono<ResponseEntity<ReportResponse<Map<String, Object>>>> getProfile() {
        return Mono.fromCallable(() -> {
            UserProfileEntity entity = userProfileService.getGlobalProfile();
            if (entity == null) {
                Map<String, Object> empty = Map.of(
                    "profile_summary", "",
                    "expertise_level", "",
                    "detail_preference", "",
                    "style_preference", "",
                    "manual_fields", List.of(),
                    "has_profile", false);
                return ResponseEntity.ok(ReportResponse.success("__global__", "OK", empty));
            }
            Map<String, Object> data = Map.of(
                "profile_summary", entity.getProfileSummary() != null ? entity.getProfileSummary() : "",
                "expertise_level", entity.getExpertiseLevel() != null ? entity.getExpertiseLevel() : "",
                "detail_preference", entity.getDetailPreference() != null ? entity.getDetailPreference() : "",
                "style_preference", entity.getStylePreference() != null ? entity.getStylePreference() : "",
                "manual_fields", parseManualFields(entity.getManualFields()),
                "updated_at", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : "",
                "has_profile", true);
            return ResponseEntity.ok(ReportResponse.success("__global__", "OK", data));
        });
    }

    @PutMapping
    public Mono<ResponseEntity<ReportResponse<Map<String, Object>>>> updateProfile(
            @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            String summary = (String) body.get("profile_summary");
            String expertise = (String) body.get("expertise_level");
            String detail = (String) body.get("detail_preference");
            String style = (String) body.get("style_preference");
            @SuppressWarnings("unchecked")
            List<String> manualFields = (List<String>) body.getOrDefault("manual_fields", List.of());

            UserProfileEntity entity = userProfileService.updateGlobalProfile(
                    summary, expertise, detail, style, manualFields);
            Map<String, Object> data = Map.of(
                "profile_summary", entity.getProfileSummary() != null ? entity.getProfileSummary() : "",
                "expertise_level", entity.getExpertiseLevel() != null ? entity.getExpertiseLevel() : "",
                "detail_preference", entity.getDetailPreference() != null ? entity.getDetailPreference() : "",
                "style_preference", entity.getStylePreference() != null ? entity.getStylePreference() : "",
                "manual_fields", parseManualFields(entity.getManualFields()),
                "updated_at", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : "");
            return ResponseEntity.ok(ReportResponse.success("__global__", "画像已更新", data));
        });
    }

    @PostMapping("/reset")
    public Mono<ResponseEntity<ReportResponse<Map<String, Object>>>> resetManualFields() {
        return Mono.fromCallable(() -> {
            UserProfileEntity entity = userProfileService.resetGlobalManualFields();
            if (entity == null) {
                return ResponseEntity.ok(ReportResponse.<Map<String, Object>>error("__global__", "暂无画像可重置"));
            }
            Map<String, Object> data = Map.of(
                "manual_fields", List.of(),
                "message", "手动覆盖已重置，下次对话后自动提取将更新全部字段");
            return ResponseEntity.ok(ReportResponse.success("__global__", "已重置", data));
        });
    }

    private List<String> parseManualFields(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; mvn compile -q`

Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/top/lanshan/manmu/api/UserProfileController.java
git commit -m "feat: UserProfileController — 全局画像 GET/PUT/reset API"
```

---

### Task 10: 前端 — 导航栏 + 路由

**Files:**
- Modify: `ui-vue3/src/components/layout/index.vue`
- Modify: `ui-vue3/src/router/defaultRoutes.ts`

- [ ] **Step 1: 修改 layout/index.vue — 新增知识库导航标签**

在 `layout/index.vue` 中：

1. 导入 `DatabaseOutlined` 图标
2. 在 `navItems` 中插入知识库标签
3. 在 `switchMode` 中新增 `/knowledge` 路由

```typescript
// 在 import 中添加
import {
  DatabaseOutlined,  // 新增
  DeleteOutlined,
  EnvironmentOutlined,
  MessageOutlined,
  PlusOutlined,
  SettingOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
```

```typescript
// 修改 navItems computed（替换原 navItems 逻辑）
const navItems = computed(() => {
  const items = [
    { value: 'chat', label: '对话', icon: MessageOutlined },
    { value: 'settings', label: '模型', icon: SettingOutlined },
  ]
  if (capabilities.value.skillEnabled) {
    items.splice(1, 0, { value: 'skills', label: 'Skill', icon: ToolOutlined })
  }
  if (capabilities.value.mcpEnabled) {
    const modelIndex = items.findIndex(item => item.value === 'settings')
    items.splice(modelIndex, 0, { value: 'mcp', label: 'MCP 工具', icon: EnvironmentOutlined })
  }
  // 知识库标签 — RAG 启用时显示
  if (capabilities.value.ragEnabled) {
    const modelIndex = items.findIndex(item => item.value === 'settings')
    items.splice(modelIndex, 0, { value: 'knowledge', label: '知识库', icon: DatabaseOutlined })
  }
  return items
})
```

```typescript
// 修改 currentMode computed
const currentMode = computed(() => {
  if (route.path.startsWith('/skills')) return 'skills'
  if (route.path.startsWith('/mcp')) return 'mcp'
  if (route.path.startsWith('/settings')) return 'settings'
  if (route.path.startsWith('/knowledge')) return 'knowledge'  // 新增
  return 'chat'
})
```

```typescript
// 修改 switchMode 函数，在 else if (mode === 'mcp') 之后添加
function switchMode(mode: string) {
  if (mode === 'chat') {
    router.push(conversationStore.curConvKey ? `/chat/${conversationStore.curConvKey}` : '/chat')
  } else if (mode === 'skills' && !capabilities.value.skillEnabled) {
    router.push('/chat')
  } else if (mode === 'mcp' && !capabilities.value.mcpEnabled) {
    router.push('/chat')
  } else if (mode === 'knowledge' && !capabilities.value.ragEnabled) {
    router.push('/chat')
  } else {
    router.push(`/${mode}`)
  }
}
```

- [ ] **Step 2: 修改 defaultRoutes.ts — 新增 /knowledge 路由**

在 `defaultRoutes.ts` 的 children 数组中，`/mcp` 路由之后添加：

```typescript
{
  path: '/knowledge',
  name: 'knowledge',
  component: () => import('../views/knowledge/index.vue'),
  meta: { icon: 'knowledge', fullscreen: true },
},
```

- [ ] **Step 3: 验证前端编译**

Run: `cd ui-vue3 && npx vue-tsc --noEmit 2>&1 | Select-Object -First 20`

Expected: 无类型错误（knowledge 组件尚未创建时可能报错，但路由注册本身无问题）

- [ ] **Step 4: 提交**

```bash
git add ui-vue3/src/components/layout/index.vue ui-vue3/src/router/defaultRoutes.ts
git commit -m "feat: 导航栏新增知识库标签和路由"
```

---

### Task 11: 前端 — 知识库 API 服务

**Files:**
- Create: `ui-vue3/src/services/api/knowledge.ts`

- [ ] **Step 1: 创建 API 服务**

```typescript
// ui-vue3/src/services/api/knowledge.ts
import { get, post, del } from '@/utils/request'

export interface RagDocumentItem {
  id: string
  fileName: string
  chunks: number
  uploadedAt: string
}

export interface RagDocumentListResponse {
  documents: RagDocumentItem[]
}

export interface GlobalUploadResult {
  fileName: string
  chunks: number
  scope: string
}

export interface UserProfileData {
  profile_summary: string
  expertise_level: string
  detail_preference: string
  style_preference: string
  manual_fields: string[]
  updated_at: string
  has_profile: boolean
}

export interface UserProfileUpdateRequest {
  profile_summary?: string
  expertise_level?: string
  detail_preference?: string
  style_preference?: string
  manual_fields: string[]
}

class KnowledgeService {
  /** 上传全局文档 */
  async uploadGlobalDocument(file: File): Promise<GlobalUploadResult> {
    const data = new FormData()
    data.append('file', file, file.name)
    data.append('session_id', '__global__')
    data.append('user_id', 'global')

    const { apiRequest } = await import('@/utils/request')
    const payload = await apiRequest<Record<string, unknown>>({
      method: 'POST',
      url: '/api/rag/upload?scope=global',
      data,
    })
    return {
      fileName: (payload?.file_name as string) || file.name,
      chunks: (payload?.chunks as number) || 0,
      scope: (payload?.scope as string) || 'global',
    }
  }

  /** 获取全局文档列表 */
  async listGlobalDocuments(limit = 50): Promise<RagDocumentItem[]> {
    const payload = await get<RagDocumentListResponse>(`/api/rag/documents?scope=global&limit=${limit}`)
    return payload?.documents || []
  }

  /** 删除全局文档 */
  async deleteGlobalDocument(id: string): Promise<void> {
    await del<void>(`/api/rag/documents/${id}?scope=global`)
  }

  /** 获取全局用户画像 */
  async getUserProfile(): Promise<UserProfileData> {
    return get<UserProfileData>('/api/user-profile')
  }

  /** 更新全局用户画像 */
  async updateUserProfile(request: UserProfileUpdateRequest): Promise<UserProfileData> {
    const { apiRequest } = await import('@/utils/request')
    return apiRequest<UserProfileData>({
      method: 'PUT',
      url: '/api/user-profile',
      data: request,
    })
  }

  /** 重置手动覆盖 */
  async resetManualFields(): Promise<{ message: string }> {
    return post<{ message: string }>('/api/user-profile/reset')
  }
}

export default new KnowledgeService()
```

- [ ] **Step 2: 注册到 services/index.ts**

检查 `ui-vue3/src/services/index.ts` 并添加 knowledge service 导出：

```typescript
// 在现有导出之后添加
export { default as knowledgeService } from './api/knowledge'
```

- [ ] **Step 3: 验证前端编译**

Run: `cd ui-vue3 && npx vue-tsc --noEmit 2>&1 | Select-Object -First 20`

Expected: 无类型错误

- [ ] **Step 4: 提交**

```bash
git add ui-vue3/src/services/api/knowledge.ts ui-vue3/src/services/index.ts
git commit -m "feat: 知识库前端 API 服务"
```

---

### Task 12: 前端 — 知识库页面组件

**Files:**
- Create: `ui-vue3/src/views/knowledge/index.vue`

- [ ] **Step 1: 创建知识库页面组件**

```vue
<!-- ui-vue3/src/views/knowledge/index.vue -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  FileOutlined,
  ReloadOutlined,
  SaveOutlined,
  UploadOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import knowledgeService from '@/services/api/knowledge'
import type { RagDocumentItem, UserProfileData } from '@/services/api/knowledge'
import {
  createRagUploadItem,
  completeRagUploadItem,
  failRagUploadItem,
  formatFileSize,
  ragUploadStatusLabel,
  ragUploadStatusColor,
  ragFileTypeLabel,
  validateRagUploadFile,
  RAG_UPLOAD_FORMAT_HINT,
} from '@/views/chat/ragUpload'
import type { RagUploadItem } from '@/views/chat/ragUpload'

// ========== 全局文档 ==========
const documents = ref<RagDocumentItem[]>([])
const docLoading = ref(false)
const docError = ref('')
const uploading = ref(false)
const uploadItems = ref<RagUploadItem[]>([])

async function loadDocuments() {
  docLoading.value = true
  docError.value = ''
  try {
    documents.value = await knowledgeService.listGlobalDocuments()
  } catch (error: unknown) {
    docError.value = (error as Error)?.message || '加载文档列表失败'
  } finally {
    docLoading.value = false
  }
}

async function handleUpload(file: File) {
  const validation = validateRagUploadFile(file)
  if (!validation.valid) {
    message.warning(validation.error || '文件校验失败')
    return false
  }

  uploading.value = true
  const item = createRagUploadItem(file, '__global__')
  uploadItems.value = [item, ...uploadItems.value].slice(0, 3)

  try {
    const result = await knowledgeService.uploadGlobalDocument(file)
    uploadItems.value = uploadItems.value.map(i =>
      i.id === item.id ? completeRagUploadItem(i, { ...result, sessionId: '__global__' }) : i
    )
    message.success(`${file.name} 上传成功，已切 ${result.chunks} 块`)
    await loadDocuments()
  } catch (error: unknown) {
    uploadItems.value = uploadItems.value.map(i =>
      i.id === item.id ? failRagUploadItem(i, error) : i
    )
    message.error(`上传失败：${(error as Error)?.message || '未知错误'}`)
  } finally {
    uploading.value = false
  }
  return false
}

async function deleteDocument(id: string, fileName: string) {
  try {
    await knowledgeService.deleteGlobalDocument(id)
    message.success(`已删除 ${fileName}`)
    documents.value = documents.value.filter(d => d.id !== id)
  } catch (error: unknown) {
    message.error(`删除失败：${(error as Error)?.message || '未知错误'}`)
  }
}

function formatTime(value: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// ========== 用户画像 ==========
const profile = ref<UserProfileData | null>(null)
const profileLoading = ref(false)
const profileError = ref('')
const editing = ref(false)
const editForm = ref({
  profile_summary: '',
  expertise_level: '',
  detail_preference: '',
  style_preference: '',
  manual_fields: [] as string[],
})

const expertiseOptions = ['beginner', 'intermediate', 'advanced']
const detailOptions = ['concise', 'balanced', 'comprehensive']
const styleOptions = ['practical', 'theoretical', 'mixed']

const expertiseLabels: Record<string, string> = {
  beginner: '初学者',
  intermediate: '中级',
  advanced: '高级',
}
const detailLabels: Record<string, string> = {
  concise: '简洁',
  balanced: '均衡',
  comprehensive: '详细',
}
const styleLabels: Record<string, string> = {
  practical: '实践',
  theoretical: '理论',
  mixed: '混合',
}

async function loadProfile() {
  profileLoading.value = true
  profileError.value = ''
  try {
    profile.value = await knowledgeService.getUserProfile()
  } catch (error: unknown) {
    profileError.value = (error as Error)?.message || '加载画像失败'
  } finally {
    profileLoading.value = false
  }
}

function startEdit() {
  if (!profile.value) return
  editForm.value = {
    profile_summary: profile.value.profile_summary,
    expertise_level: profile.value.expertise_level,
    detail_preference: profile.value.detail_preference,
    style_preference: profile.value.style_preference,
    manual_fields: [...profile.value.manual_fields],
  }
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

function toggleManualField(field: string) {
  const idx = editForm.value.manual_fields.indexOf(field)
  if (idx >= 0) {
    editForm.value.manual_fields.splice(idx, 1)
  } else {
    editForm.value.manual_fields.push(field)
  }
}

function isManual(field: string): boolean {
  return editing.value
    ? editForm.value.manual_fields.includes(field)
    : (profile.value?.manual_fields || []).includes(field)
}

async function saveProfile() {
  try {
    profile.value = await knowledgeService.updateUserProfile(editForm.value)
    editing.value = false
    message.success('画像已更新')
  } catch (error: unknown) {
    message.error(`保存失败：${(error as Error)?.message || '未知错误'}`)
  }
}

async function resetProfile() {
  try {
    await knowledgeService.resetManualFields()
    message.success('手动覆盖已重置')
    await loadProfile()
  } catch (error: unknown) {
    message.error(`重置失败：${(error as Error)?.message || '未知错误'}`)
  }
}

// ========== 生命周期 ==========
const hasProfile = computed(() => profile.value?.has_profile === true)

onMounted(() => {
  loadDocuments()
  loadProfile()
})
</script>

<template>
  <div class="knowledge-page">
    <!-- 全局知识库 -->
    <a-card class="section-card">
      <template #title>
        <div class="section-header">
          <span><DatabaseOutlined /> 全局知识库</span>
          <a-upload
            :before-upload="handleUpload"
            :show-upload-list="false"
            :disabled="uploading"
          >
            <a-button type="primary" :loading="uploading">
              <UploadOutlined />
              上传文档
            </a-button>
          </a-upload>
        </div>
      </template>
      <p class="section-desc">上传的文档对所有会话生效，用于 RAG 语义检索。{{ RAG_UPLOAD_FORMAT_HINT }}</p>

      <a-spin :spinning="docLoading">
        <a-alert
          v-if="docError"
          type="error"
          :message="docError"
          show-icon
          style="margin-bottom: 16px"
        />

        <div v-if="uploadItems.length" class="upload-items">
          <div
            v-for="item in uploadItems"
            :key="item.id"
            class="upload-item"
            :class="item.status"
          >
            <FileOutlined />
            <span class="upload-name">{{ item.fileName }}</span>
            <a-tag :color="ragUploadStatusColor(item.status)" size="small">
              {{ ragUploadStatusLabel(item) }}
            </a-tag>
            <span v-if="item.error" class="upload-error">{{ item.error }}</span>
          </div>
        </div>

        <a-empty v-if="!docLoading && !docError && documents.length === 0 && uploadItems.length === 0"
          description="尚未上传全局文档" />

        <div v-if="documents.length" class="doc-list">
          <div v-for="doc in documents" :key="doc.id" class="doc-item">
            <div class="doc-info">
              <FileOutlined class="doc-icon" />
              <span class="doc-name">{{ doc.fileName }}</span>
              <a-tag color="blue" size="small">{{ doc.chunks }} 块</a-tag>
              <span class="doc-time">{{ formatTime(doc.uploadedAt) }}</span>
            </div>
            <a-popconfirm
              :title="`确定删除 ${doc.fileName}？`"
              ok-text="删除"
              cancel-text="取消"
              @confirm="deleteDocument(doc.id, doc.fileName)"
            >
              <a-button size="small" type="text" danger>
                <DeleteOutlined />
              </a-button>
            </a-popconfirm>
          </div>
        </div>
      </a-spin>
    </a-card>

    <!-- 用户画像 -->
    <a-card class="section-card">
      <template #title>
        <div class="section-header">
          <span><UserOutlined /> 用户画像</span>
          <a-space>
            <template v-if="editing">
              <a-button size="small" @click="cancelEdit">取消</a-button>
              <a-button size="small" type="primary" @click="saveProfile">
                <SaveOutlined /> 保存
              </a-button>
            </template>
            <template v-else>
              <a-button size="small" :disabled="!hasProfile" @click="resetProfile">
                <ReloadOutlined /> 重置手动覆盖
              </a-button>
              <a-button size="small" type="primary" :disabled="!hasProfile" @click="startEdit">
                <EditOutlined /> 编辑
              </a-button>
            </template>
          </a-space>
        </div>
      </template>
      <p class="section-desc">系统根据你的对话自动构建，可手动覆盖。手动值优先于自动提取。</p>

      <a-spin :spinning="profileLoading">
        <a-alert
          v-if="profileError"
          type="error"
          :message="profileError"
          show-icon
          style="margin-bottom: 16px"
        />

        <a-empty v-if="!profileLoading && !profileError && !hasProfile"
          description="暂无画像信息，开始对话后系统会自动构建" />

        <div v-if="hasProfile || editing" class="profile-grid">
          <!-- 角色背景 -->
          <div class="profile-field">
            <div class="field-label">
              角色背景
              <a-tag v-if="isManual('profile_summary')" color="orange" size="small">手动</a-tag>
              <a-tag v-else color="default" size="small">自动</a-tag>
            </div>
            <template v-if="editing">
              <a-input
                v-model:value="editForm.profile_summary"
                placeholder="描述你的角色和背景"
              />
              <a-button
                size="small"
                type="link"
                @click="toggleManualField('profile_summary')"
              >
                {{ isManual('profile_summary') ? '取消手动覆盖' : '标记为手动覆盖' }}
              </a-button>
            </template>
            <div v-else class="field-value">{{ profile?.profile_summary || '—' }}</div>
          </div>

          <!-- 专业水平 -->
          <div class="profile-field">
            <div class="field-label">
              专业水平
              <a-tag v-if="isManual('expertise_level')" color="orange" size="small">手动</a-tag>
              <a-tag v-else color="default" size="small">自动</a-tag>
            </div>
            <template v-if="editing">
              <a-select
                v-model:value="editForm.expertise_level"
                :options="expertiseOptions.map(o => ({ value: o, label: expertiseLabels[o] || o }))"
                style="width: 100%"
              />
              <a-button
                size="small"
                type="link"
                @click="toggleManualField('expertise_level')"
              >
                {{ isManual('expertise_level') ? '取消手动覆盖' : '标记为手动覆盖' }}
              </a-button>
            </template>
            <div v-else class="field-value">
              {{ expertiseLabels[profile?.expertise_level || ''] || profile?.expertise_level || '—' }}
            </div>
          </div>

          <!-- 详细程度 -->
          <div class="profile-field">
            <div class="field-label">
              详细程度
              <a-tag v-if="isManual('detail_preference')" color="orange" size="small">手动</a-tag>
              <a-tag v-else color="default" size="small">自动</a-tag>
            </div>
            <template v-if="editing">
              <a-select
                v-model:value="editForm.detail_preference"
                :options="detailOptions.map(o => ({ value: o, label: detailLabels[o] || o }))"
                style="width: 100%"
              />
              <a-button
                size="small"
                type="link"
                @click="toggleManualField('detail_preference')"
              >
                {{ isManual('detail_preference') ? '取消手动覆盖' : '标记为手动覆盖' }}
              </a-button>
            </template>
            <div v-else class="field-value">
              {{ detailLabels[profile?.detail_preference || ''] || profile?.detail_preference || '—' }}
            </div>
          </div>

          <!-- 风格偏好 -->
          <div class="profile-field">
            <div class="field-label">
              风格偏好
              <a-tag v-if="isManual('style_preference')" color="orange" size="small">手动</a-tag>
              <a-tag v-else color="default" size="small">自动</a-tag>
            </div>
            <template v-if="editing">
              <a-select
                v-model:value="editForm.style_preference"
                :options="styleOptions.map(o => ({ value: o, label: styleLabels[o] || o }))"
                style="width: 100%"
              />
              <a-button
                size="small"
                type="link"
                @click="toggleManualField('style_preference')"
              >
                {{ isManual('style_preference') ? '取消手动覆盖' : '标记为手动覆盖' }}
              </a-button>
            </template>
            <div v-else class="field-value">
              {{ styleLabels[profile?.style_preference || ''] || profile?.style_preference || '—' }}
            </div>
          </div>
        </div>
      </a-spin>
    </a-card>
  </div>
</template>

<style lang="less" scoped>
.knowledge-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.section-card {
  border-radius: 12px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.section-desc {
  color: #7a8798;
  font-size: 13px;
  margin-bottom: 16px;
}

.upload-items {
  margin-bottom: 16px;
}

.upload-item {
  align-items: center;
  border-radius: 8px;
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 6px;
  background: #f5f7fb;
}

.upload-item.success {
  background: #eaf7ee;
}

.upload-item.error {
  background: #fff0f0;
}

.upload-name {
  font-weight: 500;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-error {
  color: #c32f35;
  font-size: 12px;
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.doc-item {
  align-items: center;
  border: 1px solid #e8edf4;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  padding: 10px 14px;
  transition: border-color 0.2s;
}

.doc-item:hover {
  border-color: #2356f6;
}

.doc-info {
  align-items: center;
  display: flex;
  gap: 10px;
  min-width: 0;
}

.doc-icon {
  color: #2356f6;
}

.doc-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-time {
  color: #7a8798;
  font-size: 12px;
}

.profile-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  align-items: center;
  color: #263244;
  display: flex;
  font-weight: 600;
  gap: 8px;
}

.field-value {
  color: #4a5568;
  font-size: 14px;
  padding: 8px 12px;
  background: #f5f7fb;
  border-radius: 6px;
}
</style>
```

- [ ] **Step 2: 验证前端编译**

Run: `cd ui-vue3 && npx vue-tsc --noEmit 2>&1 | Select-Object -First 30`

Expected: 无类型错误

- [ ] **Step 3: 提交**

```bash
git add ui-vue3/src/views/knowledge/index.vue
git commit -m "feat: 知识库页面 — 全局文档上传和用户画像展示/编辑"
```

---

### Task 13: 端到端验证

- [ ] **Step 1: 启动后端**

```bash
cd C:/MainData/code/Claude_project/M-agent
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
mvn spring-boot:run '-Dspring-boot.run.profiles=prod' > target/backend.log 2>&1 &
echo $! > target/backend.pid
```

等待启动完成（检查日志出现 "Started DeepResearchMvpApplication"）。

- [ ] **Step 2: 测试全局文档上传**

```bash
# 创建测试文件
echo "我喜欢吃辣的食物，尤其是四川火锅和湖南菜。" > target/test-global-doc.txt

# 上传全局文档
curl.exe -s -X POST "http://localhost:18080/api/rag/upload?scope=global" -F "file=@target/test-global-doc.txt" -F "session_id=__global__" -F "user_id=global"
```

Expected: 返回成功，chunks > 0

- [ ] **Step 3: 测试文档列表**

```bash
curl.exe -s "http://localhost:18080/api/rag/documents?scope=global"
```

Expected: 返回包含刚上传文档的列表

- [ ] **Step 4: 测试用户画像 API**

```bash
# 获取画像
curl.exe -s "http://localhost:18080/api/user-profile"

# 更新画像
$profileJson = '{"profile_summary":"Java 开发者","expertise_level":"advanced","manual_fields":["profile_summary","expertise_level"]}'
$profileJson | Out-File -Encoding utf8 target/profile-update.json
curl.exe -s -X PUT "http://localhost:18080/api/user-profile" -H "Content-Type: application/json" --data-binary "@target/profile-update.json"

# 重置
curl.exe -s -X POST "http://localhost:18080/api/user-profile/reset"
```

Expected: 各接口返回正确数据

- [ ] **Step 5: 启动前端并验证页面**

```bash
cd C:/MainData/code/Claude_project/M-agent/ui-vue3
npm run dev
```

在浏览器中打开 http://localhost:5173/knowledge，验证：
1. 全局文档列表显示
2. 上传文档功能正常
3. 用户画像展示正确
4. 编辑/保存/重置功能正常

- [ ] **Step 6: 停止服务**

```bash
$pid = Get-Content target/backend.pid
Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
```

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat: 全局知识库和用户画像功能完成"
```
