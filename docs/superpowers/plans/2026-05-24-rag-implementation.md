# RAG 检索增强生成 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 M-agent 中实现 RAG 检索增强生成能力，支持用户上传文件和专业知识库两种检索源，将检索结果注入研究工作流。

**Architecture:** 基于现有 PostgreSQL + pgvector 扩展作为向量存储（免引入 Elasticsearch），通过 DashScope text-embedding-v1 做文档与查询向量化。在 Graph 工作流中插入两个 RAG 节点：`user_file_rag`（用户文件检索，位于 `rewrite_multi_query` 之后）和 `professional_kb_rag`（专业知识库检索，位于 `research_team` 之后）。RAG 检索结果通过 `ResearchState` 传递，planner 和 reporter 在生成阶段消费。

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring AI 1.0.0, pgvector, DashScope text-embedding-v1, Apache Tika, TokenTextSplitter

---

## 文件结构总览

| 层 | 新建文件 | 修改文件 |
|---|---------|---------|
| **配置** | `config/RagProperties.java` | `application.yml`, `pom.xml` |
| **向量存储** | `config/PgVectorStoreConfiguration.java` | — |
| **摄入** | `rag/RagDataController.java`, `rag/VectorStoreDataIngestionService.java` | — |
| **RAG 节点** | `node/UserFileRagNode.java`, `node/ProfessionalKbDecisionNode.java`, `node/ProfessionalKbRagNode.java` | — |
| **检索策略** | `rag/RagRetriever.java` | — |
| **Prompt** | `src/main/resources/prompts/rag.md` | — |
| **Graph** | — | `graph/ResearchGraphBuilder.java` |
| **状态** | — | `model/ResearchState.java` |
| **Flyway** | `src/main/resources/db/migration/V3__enable_pgvector.sql` | — |
| **测试** | 各对应 `*Test.java` 文件 | 现有测试按需更新 |

---

## 阶段 1：RAG 基础设施搭建

> **目标**：引入 pgvector、Tika、Spring AI VectorStore 依赖，配置 Embedding 模型和向量存储，创建 Flyway 迁移开启 pgvector 扩展，建立 RagProperties 配置。作为独立验证点：确认 pgvector 可用、embedding 模型可产生向量、向量存储可读写。

### 关键决策
- **pgvector 而非 Elasticsearch**：M-agent 已有 PostgreSQL，pgvector 零运维成本。Spring AI 的 `PgVectorStore` 和 `ElasticsearchVectorStore` 共享 `VectorStore` 接口，后续切换无代码改动。
- **DashScope text-embedding-v1**：与现有 DashScope chat model 共用 API key 和 provider 配置，不走额外供应商。
- **Tika 文档阅读**：`spring-ai-tika-document-reader` 支持 PDF、DOCX、MD、TXT 等常见格式，一行代码读取文件内容。

### Task 1.1: 添加 Maven 依赖

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 在 pom.xml 添加 pgvector、Tika、Spring AI VectorStore 依赖**

在 `<dependencies>` 区块末尾（`spring-boot-starter-test` 之前）添加：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tika-document-reader</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
```

在 `pom.xml:20-24` 的 `<properties>` 中不需要额外声明版本，Spring AI BOM 已统一管理。

- [ ] **Step 2: 验证依赖解析**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B dependency:resolve 2>&1 | tail -5
```

Expected: BUILD SUCCESS，包含 `spring-ai-tika-document-reader` 和 `spring-ai-starter-vector-store-pgvector` 解析成功。

- [ ] **Step 3: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add pom.xml && git commit -m "引入 RAG 基础设施依赖：pgvector + Tika"
```

### Task 1.2: Flyway 迁移开启 pgvector 扩展

**Files:**
- Create: `src/main/resources/db/migration/V3__enable_pgvector.sql`

- [ ] **Step 1: 创建迁移文件**

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

一行足够——pgvector 扩展自带所有向量操作符（`<->` cosine distance, `<=>` L2 distance, `<#>` inner product）。

- [ ] **Step 2: 验证迁移执行**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B spring-boot:run -Dspring-boot.run.profiles=real-model -Dspring-boot.run.arguments=--server.port=18080 2>&1 | grep "Successfully validated"
# Expected: "Successfully validated 3 migrations"
```

手动验证 pgvector 已安装：

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "SELECT * FROM pg_extension WHERE extname='vector';"
# Expected: 返回一行数据，extname=vector
```

关闭后端后继续。

- [ ] **Step 3: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/resources/db/migration/V3__enable_pgvector.sql && git commit -m "开启 pgvector 扩展"
```

### Task 1.3: RagProperties 配置类

**Files:**
- Create: `src/main/java/top/lanshan/manmu/config/RagProperties.java`
- Test: `src/test/java/top/lanshan/manmu/config/RagPropertiesTest.java`

- [ ] **Step 1: 创建 RagProperties**

参考 deepresearch-main `RagProperties.java` 的精简版。仅包含当前阶段需要的字段：

```java
package top.lanshan.manmu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mvp.rag")
public class RagProperties {

    private boolean enabled = false;

    private int topK = 5;

    private double similarityThreshold = 0.7;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

}
```

- [ ] **Step 2: 编写 RagPropertiesTest**

```java
package top.lanshan.manmu.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesTest {

    @Test
    void defaultsDisableRag() {
        RagProperties properties = new RagProperties();
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void defaultTopKIsFive() {
        RagProperties properties = new RagProperties();
        assertThat(properties.getTopK()).isEqualTo(5);
        assertThat(properties.getSimilarityThreshold()).isEqualTo(0.7);
    }

    @Test
    void settersOverrideDefaults() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        properties.setTopK(10);
        properties.setSimilarityThreshold(0.85);

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getTopK()).isEqualTo(10);
        assertThat(properties.getSimilarityThreshold()).isEqualTo(0.85);
    }

}
```

- [ ] **Step 3: 运行测试**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B "-Dtest=RagPropertiesTest" test 2>&1 | tail -5
```

Expected: Tests run: 3, Failures: 0, BUILD SUCCESS

- [ ] **Step 4: 在 application.yml 添加 RAG 默认配置**

在 `src/main/resources/application.yml` 的 `mvp:` 区块下添加：

```yaml
  rag:
    enabled: false
    top-k: 5
    similarity-threshold: 0.7
```

- [ ] **Step 5: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/config/RagProperties.java src/test/java/top/lanshan/manmu/config/RagPropertiesTest.java src/main/resources/application.yml && git commit -m "添加 RAG 配置属性类"
```

### Task 1.4: PgVectorStore 配置

**Files:**
- Create: `src/main/java/top/lanshan/manmu/config/PgVectorStoreConfiguration.java`

- [ ] **Step 1: 创建 PgVectorStoreConfiguration**

```java
package top.lanshan.manmu.config;

import javax.sql.DataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(prefix = "mvp.rag", name = "enabled", havingValue = "true")
public class PgVectorStoreConfiguration {

    @Bean
    PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .vectorTableName("rag_vectors")
            .dimensions(1536)
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .build();
    }

}
```

`dimensions=1536` 对应 DashScope text-embedding-v1 输出维度。`COSINE_DISTANCE` 使用 pgvector `<->` 操作符。`@ConditionalOnProperty` 保证仅在 `mvp.rag.enabled=true` 时创建此 Bean，不影响现有路径。

**注意**：`PgVectorStore.builder()` 会在初始化时自动创建 `rag_vectors` 表（如果不存在），无需手写 DDL。

- [ ] **Step 2: 在 application.yml 启用 Embedding 模型**

`spring-ai-alibaba-starter-dashscope` 已提供 `DashScopeEmbeddingModel`，只需在 `application.yml` 中声明模型名：

```yaml
spring:
  ai:
    dashscope:
      embedding:
        options:
          model: text-embedding-v1
```

- [ ] **Step 3: 编写 PgVectorStoreConfigurationTest**

```java
package top.lanshan.manmu.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PgVectorStoreConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class);

    @Test
    void pgVectorStoreIsNotCreatedWhenRagDisabled() {
        contextRunner
            .withPropertyValues("mvp.rag.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(PgVectorStore.class));
    }

    @Test
    void pgVectorStoreIsCreatedWhenRagEnabledWithRealDatabase() {
        // 此测试需要 PostgreSQL + pgvector 运行中
        // 标记为集成测试，命名以 IT 结尾
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {
    }

}
```

- [ ] **Step 4: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/config/PgVectorStoreConfiguration.java src/test/java/top/lanshan/manmu/config/PgVectorStoreConfigurationTest.java src/main/resources/application.yml && git commit -m "配置 PgVectorStore 向量存储"
```

### Task 1.5: 真实 E2E 验证基础设施

- [ ] **Step 1: 启动后端并验证 Embedding 模型可用**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B spring-boot:run -Dspring-boot.run.profiles=real-model -Dspring-boot.run.arguments="--server.port=18080 --mvp.rag.enabled=true" > target/rag-stage1-e2e.log 2>&1 &
# 等待 15 秒启动
```

通过 curl 验证 embedding 端点（DashScope embedding 通过 Spring AI 的 EmbeddingModel 接口调用，后端无独立 HTTP 端点；通过 actuator health 确认上下文加载成功）：

```bash
curl -s http://localhost:18080/actuator/health 2>&1
```

检查日志确认 `PgVectorStore` Bean 已创建：

```bash
grep -c "PgVectorStore" target/rag-stage1-e2e.log
# Expected: 非零（至少有初始化日志）
```

- [ ] **Step 2: 验证 pgvector 表已自动创建**

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "\d rag_vectors"
# Expected: 显示 rag_vectors 表结构（id, content, metadata, embedding 列）
```

- [ ] **Step 3: 关闭后端并确认端口释放**

```bash
powershell -Command "Stop-Process -Id (Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue).OwningProcess -Force" 2>/dev/null
sleep 2
powershell -Command "Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue"
# Expected: 无输出（端口已释放）
```

- [ ] **Step 4: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add -A && git commit -m "阶段 1 完成：RAG 基础设施（pgvector + Embedding 模型 + 向量存储）"
```

---

## 阶段 2：文档摄入管道

> **目标**：实现文件上传 API 和文档摄入管道（Tika 读取 → TokenTextSplitter 切块 → Embedding 向量化 → pgvector 存储）。作为独立验证点：上传一个 Markdown 文件 → 向量存储可检索到相关块。

### Task 2.1: VectorStoreDataIngestionService

**Files:**
- Create: `src/main/java/top/lanshan/manmu/rag/VectorStoreDataIngestionService.java`
- Test: `src/test/java/top/lanshan/manmu/rag/VectorStoreDataIngestionServiceTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package top.lanshan.manmu.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreDataIngestionServiceTest {

    @Test
    void ingestSplitsMarkdownContentIntoChunks() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        VectorStoreDataIngestionService service = new VectorStoreDataIngestionService(vectorStore);
        byte[] content = "# Title\n\nThis is paragraph one.\n\n## Section\n\nThis is paragraph two with enough tokens to ensure splitting occurs. ".repeat(50).getBytes();

        int chunks = service.ingest(new ByteArrayResource(content), "session-1", "user-1");

        assertThat(chunks).isGreaterThan(1);
        assertThat(vectorStore.acceptedDocuments).hasSize(chunks);
        assertThat(vectorStore.acceptedDocuments.get(0).getMetadata())
            .containsEntry("source_type", "user_upload")
            .containsEntry("session_id", "session-1")
            .containsEntry("user_id", "user-1");
        assertThat(vectorStore.acceptedDocuments.get(0).getMetadata())
            .containsKeys("chunk_id", "original_filename", "upload_timestamp");
    }

}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn -B "-Dtest=VectorStoreDataIngestionServiceTest" test 2>&1 | tail -5
# Expected: FAIL (RecordingVectorStore / VectorStoreDataIngestionService 不存在)
```

- [ ] **Step 3: 实现 VectorStoreDataIngestionService**

```java
package top.lanshan.manmu.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VectorStoreDataIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreDataIngestionService.class);

    private final VectorStore vectorStore;

    private final TokenTextSplitter textSplitter;

    public VectorStoreDataIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.textSplitter = TokenTextSplitter.builder()
            .withDefaultChunkSize(800)
            .withMinChunkSizeToSplit(5)
            .withMaxNumChunks(100)
            .withKeepSeparator(true)
            .withOverlap(100)
            .build();
    }

    public int ingest(Resource resource, String sessionId, String userId) {
        List<Document> documents = new TikaDocumentReader(resource).read();
        logger.info("Read {} documents from resource", documents.size());

        List<Document> chunks = textSplitter.split(documents);
        logger.info("Split into {} chunks", chunks.size());

        Instant timestamp = Instant.now();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = new java.util.HashMap<>(chunk.getMetadata());
            metadata.put("source_type", "user_upload");
            metadata.put("session_id", sessionId);
            metadata.put("user_id", userId);
            metadata.put("chunk_id", i);
            metadata.put("original_filename", resource.getFilename());
            metadata.put("upload_timestamp", timestamp.toString());
            metadata.put("file_size", resource.contentLength());
            chunk.getMetadata().putAll(metadata);
        }

        vectorStore.add(chunks);
        logger.info("Ingested {} chunks into vector store for session={}", chunks.size(), sessionId);
        return chunks.size();
    }

}
```

- [ ] **Step 4: 实现 RecordingVectorStore 测试辅助类**

在同一个测试文件中添加：

```java
class RecordingVectorStore implements VectorStore {

    final List<Document> acceptedDocuments = new ArrayList<>();

    @Override
    public void add(List<Document> documents) {
        acceptedDocuments.addAll(documents);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        return List.of();
    }

    // 其余 VectorStore 方法返回默认值
    @Override public List<Document> similaritySearch(String query) { return List.of(); }
    @Override public void delete(String id) {}
    @Override public void delete(List<String> idList) {}
    @Override public java.util.Optional<Document> get(String id) { return java.util.Optional.empty(); }
    @Override public java.util.List<Document> get(java.util.List<String> idList) { return java.util.List.of(); }
}
```

- [ ] **Step 5: 运行测试确认通过**

```bash
mvn -B "-Dtest=VectorStoreDataIngestionServiceTest" test 2>&1 | tail -5
# Expected: Tests run: 1, Failures: 0, BUILD SUCCESS
```

- [ ] **Step 6: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/rag/VectorStoreDataIngestionService.java src/test/java/top/lanshan/manmu/rag/VectorStoreDataIngestionServiceTest.java && git commit -m "实现文档摄入管道：Tika 读取 + TokenTextSplitter 切块 + 向量存储"
```

### Task 2.2: RagDataController 文件上传 API

**Files:**
- Create: `src/main/java/top/lanshan/manmu/rag/RagDataController.java`
- Test: `src/test/java/top/lanshan/manmu/rag/RagDataControllerTest.java`

- [ ] **Step 1: 创建 RagDataController**

```java
package top.lanshan.manmu.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.model.ApiResponse;

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
    public Mono<ApiResponse<Map<String, Object>>> upload(
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
            .map(resource -> {
                int chunks = ingestionService.ingest(resource, finalSessionId, finalUserId);
                return ApiResponse.success(Map.of(
                    "file_name", file.filename(),
                    "chunks", chunks,
                    "session_id", finalSessionId));
            });
    }

}
```

- [ ] **Step 2: 编写 RagDataControllerTest**

```java
package top.lanshan.manmu.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RagDataControllerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class);

    @Test
    void controllerIsNotRegisteredWhenRagDisabled() {
        contextRunner
            .withPropertyValues("mvp.rag.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(RagDataController.class));
    }

    @Test
    void controllerIsRegisteredWhenRagEnabled() {
        contextRunner
            .withPropertyValues("mvp.rag.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(RagDataController.class));
    }

    @Configuration
    static class TestConfig {
        @Bean
        VectorStoreDataIngestionService ingestionService() {
            return new VectorStoreDataIngestionService(new RecordingVectorStore());
        }
    }

}
```

- [ ] **Step 3: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/rag/RagDataController.java src/test/java/top/lanshan/manmu/rag/RagDataControllerTest.java && git commit -m "添加 RAG 文件上传 API"
```

### Task 2.3: 真实 E2E 验证摄入管道

- [ ] **Step 1: 启动后端（rag.enabled=true）**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B spring-boot:run -Dspring-boot.run.profiles=real-model -Dspring-boot.run.arguments="--server.port=18080 --mvp.rag.enabled=true" > target/rag-stage2-e2e.log 2>&1 &
sleep 15
```

- [ ] **Step 2: 创建测试 Markdown 文件并上传**

```bash
echo "# RAG Test Document\n\n## Section A\n\nThis document contains information about artificial intelligence and machine learning. AI has revolutionized many industries.\n\n## Section B\n\nPython is the most popular programming language for data science. It offers libraries like NumPy and Pandas.\n\n## Section C\n\nPostgreSQL is a powerful open-source relational database. It supports advanced features like full-text search and JSON." > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-test-doc.md
```

- [ ] **Step 3: 上传文件到 RAG API**

```bash
# 切换到 DeepSeek（DashScope embedding 不依赖 chat model，无需切换）
curl -s -X POST http://localhost:18080/api/rag/upload \
  -F "file=@C:/MainData/code/Claude_project/M-agent/target/http-check/rag-test-doc.md" \
  -F "session_id=rag-stage2-test" \
  -F "user_id=test-user" 2>&1
# Expected: {"code":200,"status":"success","data":{"file_name":"rag-test-doc.md","chunks":3,"session_id":"rag-stage2-test"}}
```

- [ ] **Step 4: 验证向量存储有数据**

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "SELECT COUNT(*) FROM rag_vectors WHERE metadata->>'session_id' = 'rag-stage2-test';"
# Expected: count >= 1
```

- [ ] **Step 5: 关闭后端并确认端口释放**

```bash
powershell -Command "Stop-Process -Id (Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue).OwningProcess -Force" 2>/dev/null
sleep 2
powershell -Command "Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue"
# Expected: 无输出
```

- [ ] **Step 6: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add -A && git commit -m "阶段 2 完成：文档摄入管道（文件上传 + Tika + 切块 + pgvector 存储）"
```

---

## 阶段 3：用户文件 RAG 节点

> **目标**：实现 `UserFileRagNode`，将用户上传的文件内容作为研究上下文注入 Graph 工作流。作为独立验证点：上传文件 → 运行研究 → 报告包含文件内容。

### 关键设计

`UserFileRagNode` 插入位置：

```
rewrite_multi_query
    ├── [rag.enabled=true 且用户上传了文件] → user_file_rag → background_investigator
    └── [否则] → background_investigator（现有路径）
```

节点职责：
1. 从 `ResearchState` 获取当前 query 和 session_id
2. 在 pgvector 中按 `session_id` + `source_type=user_upload` 过滤检索
3. 将检索结果拼接为 RAG 上下文
4. 调用 `ragAgent`（使用 `rag.md` prompt）生成 RAG 回答
5. 将 `rag_content` 写入 `ResearchState`，供 planner 和 reporter 使用

### Task 3.1: RAG Prompt 文件

**Files:**
- Create: `src/main/resources/prompts/rag.md`

- [ ] **Step 1: 创建 rag.md**

从 deepresearch-main 复制并适配：

```markdown
# 角色
你是一个专门负责根据所提供的上下文信息来回答用户问题的 AI 助手。你没有先验知识，所有回答都必须严格基于上下文。

# 核心任务
你的任务是基于下方提供的 [上下文]，为 [用户问题] 生成一份结构清晰、格式严谨、专业中立的 Markdown 格式回答。

# 回答规则

### 1. 内容与准确性
- **严格忠于原文**：只使用上下文中包含的信息。如果上下文没有足够信息来回答问题，必须明确回答："根据所提供的资料，我无法找到相关问题的答案。"
- **切中要点**：只提供与问题直接相关的信息，避免无关内容和重复信息。
- **语言一致**：回答的语言必须与用户问题的语言保持一致。

### 2. 结构与格式
- **结论先行**：在回答的开头，首先给出最核心的结论或要点。
- **层级清晰**：使用 Markdown 的二级标题 (`## 标题`) 和三级标题 (`### 子标题`) 来组织内容。
- **格式严谨**：整个回答必须是美观且规范的 Markdown 格式。

### 3. 风格与引用
- **专业口吻**：以专家、客观、中立的语气进行陈述。
- **直接陈述**：直接给出答案和信息，避免使用"根据上下文..."等引导性短语。
- **标注来源**：当引用上下文中的具体信息时，以 Markdown 超链接的形式 `[来源文档](链接)` 附上来源。

---
[上下文]:
"""
{context}
"""

[用户问题]:
"""
{question}
"""
```

- [ ] **Step 2: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/resources/prompts/rag.md && git commit -m "添加 RAG 回答生成 prompt"
```

### Task 3.2: RagRetriever 检索器

**Files:**
- Create: `src/main/java/top/lanshan/manmu/rag/RagRetriever.java`
- Test: `src/test/java/top/lanshan/manmu/rag/RagRetrieverTest.java`

- [ ] **Step 1: 创建 RagRetriever**

```java
package top.lanshan.manmu.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RagRetriever {

    private static final Logger logger = LoggerFactory.getLogger(RagRetriever.class);

    private final VectorStore vectorStore;

    private final int topK;

    private final double similarityThreshold;

    public RagRetriever(VectorStore vectorStore, int topK, double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public List<Document> retrieve(String query, Map<String, Object> filterMetadata) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression filter = null;
        for (Map.Entry<String, Object> entry : filterMetadata.entrySet()) {
            Filter.Expression expr = builder.eq(entry.getKey(), entry.getValue());
            filter = filter == null ? expr : builder.and(filter, expr);
        }
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(similarityThreshold)
            .filterExpression(filter != null ? filter : new FilterExpressionBuilder().eq("source_type", "user_upload"))
            .build();
        List<Document> results = vectorStore.similaritySearch(request);
        logger.info("Retrieved {} documents for query: {}", results.size(),
                query.length() > 100 ? query.substring(0, 100) + "..." : query);
        return results;
    }

    public String buildContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "";
        }
        return documents.stream()
            .map(doc -> {
                String source = doc.getMetadata().getOrDefault("original_filename", "unknown").toString();
                return "[来源: " + source + "]\n" + doc.getText();
            })
            .collect(Collectors.joining("\n\n---\n\n"));
    }

}
```

- [ ] **Step 2: 编写 RagRetrieverTest**

```java
package top.lanshan.manmu.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrieverTest {

    @Test
    void buildContextFormatsDocumentsWithSourceLabels() {
        RagRetriever retriever = new RagRetriever(null, 5, 0.7);
        Document doc1 = new Document("Content from file A.", Map.of("original_filename", "report.md"));
        Document doc2 = new Document("Content from file B.", Map.of("original_filename", "notes.pdf"));

        String context = retriever.buildContext(List.of(doc1, doc2));

        assertThat(context).contains("[来源: report.md]");
        assertThat(context).contains("[来源: notes.pdf]");
        assertThat(context).contains("Content from file A.");
        assertThat(context).contains("Content from file B.");
        assertThat(context).contains("---");
    }

    @Test
    void buildContextReturnsEmptyForNoDocuments() {
        RagRetriever retriever = new RagRetriever(null, 5, 0.7);

        assertThat(retriever.buildContext(List.of())).isEmpty();
    }

}
```

- [ ] **Step 3: 运行测试**

```bash
mvn -B "-Dtest=RagRetrieverTest" test 2>&1 | tail -5
# Expected: Tests run: 2, Failures: 0, BUILD SUCCESS
```

- [ ] **Step 4: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/rag/RagRetriever.java src/test/java/top/lanshan/manmu/rag/RagRetrieverTest.java && git commit -m "实现 RAG 检索器：向量检索 + 上下文构建"
```

### Task 3.3: UserFileRagNode 节点

**Files:**
- Create: `src/main/java/top/lanshan/manmu/node/UserFileRagNode.java`
- Test: `src/test/java/top/lanshan/manmu/node/UserFileRagNodeTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.DefaultResourceLoader;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.StepType;
import top.lanshan.manmu.rag.RagRetriever;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserFileRagNodeTest {

    @Test
    void emitsNoEventsWhenNoDocumentsRetrieved() {
        VectorStore emptyStore = new StubVectorStore(List.of());
        RagRetriever retriever = new RagRetriever(emptyStore, 5, 0.7);
        ChatClient chatClient = ChatClient.builder(new StubChatModel("unused")).build();
        UserFileRagNode node = new UserFileRagNode(retriever, ChatClient.builder(new StubChatModel("unused")),
                new DefaultResourceLoader());
        ResearchState state = stateWithQueryAndSession("What is AI?", "session-test");

        StepVerifier.create(node.run(state)).verifyComplete();
    }

    @Test
    void emitsRagContentWhenDocumentsRetrieved() {
        Document doc = new Document("AI is artificial intelligence.", Map.of("original_filename", "notes.md"));
        VectorStore store = new StubVectorStore(List.of(doc));
        RagRetriever retriever = new RagRetriever(store, 5, 0.7);
        StubChatModel chatModel = new StubChatModel("RAG answer: AI stands for artificial intelligence.");
        UserFileRagNode node = new UserFileRagNode(retriever,
                ChatClient.builder(chatModel), new DefaultResourceLoader());
        ResearchState state = stateWithQueryAndSession("What is AI?", "session-test");

        List<ResearchEvent> events = node.run(state).collectList().block();

        assertThat(events).isNotEmpty();
        assertThat(events.get(0).node()).isEqualTo("user_file_rag");
        assertThat(events.get(0).phase()).isEqualTo("completed");
        assertThat(state.observations()).anyMatch(obs -> obs.toString().contains("RAG answer"));
    }

    private ResearchState stateWithQueryAndSession(String query, String sessionId) {
        ResearchState state = ResearchState.from(new ResearchRequest(query, "thread-1", 2));
        // ResearchState 使用 threadId 作为默认 sessionId；覆盖
        ResearchPlan plan = new ResearchPlan("Test", true, "Test plan", List.of(
                new ResearchStep("Step", "Desc", false, StepType.RESEARCH, null, "pending")));
        state.plan(plan);
        return state;
    }

    static class StubVectorStore implements VectorStore {
        private final List<Document> documents;
        StubVectorStore(List<Document> documents) { this.documents = documents; }
        @Override public void add(List<Document> docs) {}
        @Override public List<Document> similaritySearch(SearchRequest request) { return documents; }
        @Override public List<Document> similaritySearch(String query) { return documents; }
        @Override public void delete(String id) {}
        @Override public void delete(List<String> ids) {}
        @Override public java.util.Optional<Document> get(String id) { return java.util.Optional.empty(); }
        @Override public List<Document> get(List<String> ids) { return List.of(); }
    }

    static class StubChatModel implements ChatModel {
        private final String response;
        StubChatModel(String response) { this.response = response; }
        @Override public org.springframework.ai.chat.model.ChatResponse call(
                org.springframework.ai.chat.prompt.Prompt prompt) {
            return new org.springframework.ai.chat.model.ChatResponse(
                    List.of(new org.springframework.ai.chat.model.Generation(
                            new org.springframework.ai.chat.messages.AssistantMessage(response))));
        }
        @Override public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() {
            return org.springframework.ai.chat.prompt.ChatOptions.EMPTY;
        }
    }

}
```

**注意**：上述测试使用 stub `ChatModel` 而非真实 API 调用。`UserFileRagNode` 使用 `ChatClient.Builder` 构造以支持 stub。如果 `ChatClient.Builder` 注入复杂（Spring 上下文依赖），可将 `UserFileRagNode` 构造函数改为接受已构建的 `ChatClient` 或提取 `RagAgent` 接口。

- [ ] **Step 2: 实现 UserFileRagNode**

```java
package top.lanshan.manmu.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.rag.RagRetriever;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class UserFileRagNode implements ResearchNode {

    private static final Logger logger = LoggerFactory.getLogger(UserFileRagNode.class);

    private final RagRetriever retriever;

    private final ChatClient ragAgent;

    private final String ragPromptTemplate;

    public UserFileRagNode(RagRetriever retriever, ChatClient.Builder chatClientBuilder,
            ResourceLoader resourceLoader) {
        this.retriever = retriever;
        this.ragAgent = chatClientBuilder.build();
        this.ragPromptTemplate = loadPrompt(resourceLoader);
    }

    private String loadPrompt(ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource("classpath:prompts/rag.md");
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to load RAG prompt from classpath:prompts/rag.md", e);
        }
    }

    @Override
    public int order() {
        return 8;
    }

    @Override
    public String name() {
        return "user_file_rag";
    }

    @Override
    public Flux<ResearchEvent> run(ResearchState state) {
        return Flux.defer(() -> {
            String sessionId = state.sessionId();
            if (sessionId == null || sessionId.isBlank()) {
                return Flux.empty();
            }
            String query = queryForRag(state);
            logger.info("UserFileRagNode retrieving for session={}, query={}", sessionId,
                    query.length() > 80 ? query.substring(0, 80) + "..." : query);

            Map<String, Object> filters = Map.of(
                "source_type", "user_upload",
                "session_id", sessionId);
            List<Document> documents = retriever.retrieve(query, filters);
            if (documents.isEmpty()) {
                return Flux.empty();
            }

            String context = retriever.buildContext(documents);
            String prompt = ragPromptTemplate
                .replace("{context}", context)
                .replace("{question}", query);

            return Mono.fromCallable(() -> ragAgent.prompt().user(prompt).call().content())
                .flatMapMany(ragContent -> {
                    state.addObservation("[RAG] " + ragContent);
                    return Flux.just(new ResearchEvent(state.threadId(), null, null, name(), name(),
                            null, null, null, "completed", "completed",
                            "用户文件检索", "RAG context retrieved and applied", ragContent,
                            null, false, Instant.now()));
                });
        });
    }

    private String queryForRag(ResearchState state) {
        if (state.optimizedQueries() != null && !state.optimizedQueries().isEmpty()) {
            return String.join(" ", state.optimizedQueries());
        }
        return state.query();
    }

}
```

- [ ] **Step 3: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/node/UserFileRagNode.java src/test/java/top/lanshan/manmu/node/UserFileRagNodeTest.java && git commit -m "实现用户文件 RAG 检索节点"
```

### Task 3.4: 集成 UserFileRagNode 到 Graph

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`

- [ ] **Step 1: 在 ResearchGraphBuilder 添加 user_file_rag 节点常量和路由**

在 `ResearchGraphBuilder.java` 中添加常量：

```java
public static final String USER_FILE_RAG = "user_file_rag";
```

在 `addRequiredNodes()` 中按条件添加（仅 rag.enabled=true 时）：

```java
// rag.enabled 时添加 user_file_rag 节点
if (ragProperties != null && ragProperties.isEnabled()) {
    graph.addNode(USER_FILE_RAG, ResearchNodeGraphAction.async(requiredNode(USER_FILE_RAG)));
}
```

修改 `buildAdvancedAutoResearchGraph` 中的路由（`rewrite_multi_query` 之后）：

```java
// 原: graph.addEdge(REWRITE_MULTI_QUERY, BACKGROUND_INVESTIGATOR);
// 改为: 如果 rag 启用，rewrite → user_file_rag → background_investigator
if (ragProperties != null && ragProperties.isEnabled() && hasFileUpload(state)) {
    graph.addEdge(REWRITE_MULTI_QUERY, USER_FILE_RAG);
    graph.addEdge(USER_FILE_RAG, BACKGROUND_INVESTIGATOR);
} else {
    graph.addEdge(REWRITE_MULTI_QUERY, BACKGROUND_INVESTIGATOR);
}
```

但注意——Graph 是编译时构建的（build time），而 `hasFileUpload` 是运行时状态。因此在 build 阶段始终添加 `user_file_rag` 节点和 `rewrite → user_file_rag → background_investigator` 的边。`user_file_rag` 节点内部判断是否有文件可用，无文件时返回 `Flux.empty()`（透明通过）。

简化为：当 `rag.enabled=true` 且 `user_file_rag` 节点存在时，始终走 `rewrite_multi_query → user_file_rag → background_investigator`。有文件时注入上下文，无文件时透明通过。

- [ ] **Step 2: 更新 buildAdvancedPlanGateResearchGraph 同样处理**

`buildAdvancedPlanGateResearchGraph` 和 `buildAdvancedResumeGraph` 同样添加 RAG 边。

- [ ] **Step 3: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java && git commit -m "接入 user_file_rag 节点到 Graph 工作流"
```

### Task 3.5: 真实 E2E 验证 user_file_rag

- [ ] **Step 1: 启动后端（rag.enabled=true）**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B spring-boot:run -Dspring-boot.run.profiles=real-model -Dspring-boot.run.arguments="--server.port=18080 --mvp.rag.enabled=true" > target/rag-stage3-e2e.log 2>&1 &
sleep 15
```

- [ ] **Step 2: 上传包含专业知识的文件**

```bash
echo "# PostgreSQL Performance Guide\n\nPostgreSQL can handle large datasets efficiently with proper indexing. B-tree indexes are the default, but GIN indexes excel at full-text search. For vector search, pgvector uses IVFFlat indexes that dramatically speed up similarity queries.\n\nPartitioning tables by date range improves query performance when scanning recent data. Connection pooling via PgBouncer prevents connection exhaustion under high concurrency." > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-pg-guide.md

curl -s -X POST http://localhost:18080/api/rag/upload \
  -F "file=@C:/MainData/code/Claude_project/M-agent/target/http-check/rag-pg-guide.md" \
  -F "session_id=rag-stage3-e2e" \
  -F "user_id=test-user" 2>&1
```

- [ ] **Step 3: 运行研究请求**

```bash
echo '{"query":"How does PostgreSQL handle performance at scale?","thread_id":"rag-stage3","session_id":"rag-stage3-e2e","auto_accepted_plan":true}' > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-stage3-request.json

curl -s -N -X POST http://localhost:18080/chat/stream \
  -H "Content-Type: application/json" \
  --data-binary "@C:/MainData/code/Claude_project/M-agent/target/http-check/rag-stage3-request.json" \
  --max-time 300 > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-stage3-result.sse 2>&1
```

- [ ] **Step 4: 验证报告包含 RAG 上下文**

```bash
# 验证 user_file_rag 节点已执行
grep -c "user_file_rag" C:/MainData/code/Claude_project/M-agent/target/http-check/rag-stage3-result.sse
# Expected: >0

# 验证报告可读取
curl -s http://localhost:18080/api/reports/rag-stage3 2>&1 | head -c 300
# Expected: 包含 report_information 字段，内容涉及 PostgreSQL 性能相关信息
```

- [ ] **Step 5: 关闭后端并确认端口释放**

```bash
powershell -Command "Stop-Process -Id (Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue).OwningProcess -Force" 2>/dev/null
sleep 2
```

- [ ] **Step 6: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add -A && git commit -m "阶段 3 完成：用户文件 RAG 节点集成 + E2E 验证"
```

---

## 阶段 4：专业知识库决策与检索

> **目标**：实现 `ProfessionalKbDecisionNode`（LLM 驱动的 KB 选择）和 `ProfessionalKbRagNode`（按选中 KB 检索）。作为独立验证点：配置一个专业知识库 → 运行研究 → 报告包含知识库内容。

### Task 4.1: ProfessionalKbProperties 配置扩展

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/config/RagProperties.java`

- [ ] **Step 1: 在 RagProperties 中添加 ProfessionalKnowledgeBases 内部类**

```java
private final ProfessionalKnowledgeBases professionalKnowledgeBases = new ProfessionalKnowledgeBases();

public ProfessionalKnowledgeBases getProfessionalKnowledgeBases() {
    return professionalKnowledgeBases;
}

public static class ProfessionalKnowledgeBases {
    private boolean decisionEnabled = true;
    private List<KnowledgeBase> knowledgeBases = new ArrayList<>();

    public boolean isDecisionEnabled() { return decisionEnabled; }
    public void setDecisionEnabled(boolean decisionEnabled) { this.decisionEnabled = decisionEnabled; }
    public List<KnowledgeBase> getKnowledgeBases() { return knowledgeBases; }
    public void setKnowledgeBases(List<KnowledgeBase> knowledgeBases) { this.knowledgeBases = knowledgeBases; }

    public static class KnowledgeBase {
        private String id;
        private String name;
        private String description;
        private String type = "api";
        private boolean enabled = true;
        private int priority = 100;
        private Api api = new Api();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public Api getApi() { return api; }
        public void setApi(Api api) { this.api = api; }

        public static class Api {
            private String provider = "dashscope";
            private String url;
            private String apiKey;
            private String model;
            private int timeoutMs = 30000;
            private int maxResults = 5;

            public String getProvider() { return provider; }
            public void setProvider(String provider) { this.provider = provider; }
            public String getUrl() { return url; }
            public void setUrl(String url) { this.url = url; }
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
            public String getModel() { return model; }
            public void setModel(String model) { this.model = model; }
            public int getTimeoutMs() { return timeoutMs; }
            public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
            public int getMaxResults() { return maxResults; }
            public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
        }
    }
}
```

- [ ] **Step 2: 更新测试**

添加 `RagPropertiesTest`：

```java
@Test
void professionalKnowledgeBasesDefaultEmpty() {
    RagProperties properties = new RagProperties();
    assertThat(properties.getProfessionalKnowledgeBases().getKnowledgeBases()).isEmpty();
    assertThat(properties.getProfessionalKnowledgeBases().isDecisionEnabled()).isTrue();
}
```

- [ ] **Step 3: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/config/RagProperties.java src/test/java/top/lanshan/manmu/config/RagPropertiesTest.java && git commit -m "扩展 RagProperties 支持专业知识库配置"
```

### Task 4.2: ProfessionalKbDecisionNode

**Files:**
- Create: `src/main/java/top/lanshan/manmu/node/ProfessionalKbDecisionNode.java`
- Test: `src/test/java/top/lanshan/manmu/node/ProfessionalKbDecisionNodeTest.java`

- [ ] **Step 1: 实现 ProfessionalKbDecisionNode**

```java
package top.lanshan.manmu.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.config.RagProperties;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProfessionalKbDecisionNode implements ResearchNode {

    private static final Logger logger = LoggerFactory.getLogger(ProfessionalKbDecisionNode.class);

    private final ChatClient chatClient;

    private final RagProperties ragProperties;

    public ProfessionalKbDecisionNode(ChatClient.Builder chatClientBuilder, RagProperties ragProperties) {
        this.chatClient = chatClientBuilder.build();
        this.ragProperties = ragProperties;
    }

    @Override
    public int order() {
        return 32;
    }

    @Override
    public String name() {
        return "professional_kb_decision";
    }

    @Override
    public Flux<ResearchEvent> run(ResearchState state) {
        return Flux.defer(() -> {
            if (!ragProperties.getProfessionalKnowledgeBases().isDecisionEnabled()) {
                state.selectedKnowledgeBases(Collections.emptyList());
                return Flux.empty();
            }
            List<RagProperties.ProfessionalKnowledgeBases.KnowledgeBase> enabledKbs =
                    ragProperties.getProfessionalKnowledgeBases().getKnowledgeBases().stream()
                        .filter(RagProperties.ProfessionalKnowledgeBases.KnowledgeBase::isEnabled)
                        .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                        .toList();
            if (enabledKbs.isEmpty()) {
                state.selectedKnowledgeBases(Collections.emptyList());
                return Flux.empty();
            }
            String kbDescriptions = enabledKbs.stream()
                .map(kb -> "- ID: " + kb.getId() + ", Name: " + kb.getName() + ", Description: " + kb.getDescription())
                .collect(Collectors.joining("\n"));
            String prompt = buildDecisionPrompt(state.query(), kbDescriptions);

            return Mono.fromCallable(() -> chatClient.prompt().user(prompt).call().content())
                .flatMapMany(response -> {
                    List<String> selectedIds = parseSelection(response, enabledKbs);
                    state.selectedKnowledgeBases(selectedIds);
                    logger.info("ProfessionalKbDecision selected KBs: {} for query: {}",
                            selectedIds, state.query().length() > 80
                                    ? state.query().substring(0, 80) + "..." : state.query());
                    return Flux.just(new ResearchEvent(state.threadId(), null, null,
                            name(), name(), null, null, null,
                            "decision", "decision", "专业知识库选择",
                            "Selected KBs: " + String.join(", ", selectedIds),
                            selectedIds, null, false, Instant.now()));
                });
        });
    }

    private String buildDecisionPrompt(String query, String kbDescriptions) {
        return """
            You are a research assistant deciding which knowledge bases to query.
            
            User Question: %s
            
            Available Knowledge Bases:
            %s
            
            Based on the user's question, select the knowledge bases that are MOST RELEVANT.
            Respond ONLY in this format:
            SELECTED: [kb_id1, kb_id2]
            or
            SELECTED: []
            """.formatted(query, kbDescriptions);
    }

    private List<String> parseSelection(String response,
            List<RagProperties.ProfessionalKnowledgeBases.KnowledgeBase> enabledKbs) {
        List<String> validIds = enabledKbs.stream()
            .map(RagProperties.ProfessionalKnowledgeBases.KnowledgeBase::getId)
            .toList();
        int start = response.indexOf("SELECTED:");
        if (start < 0) {
            return Collections.emptyList();
        }
        String selection = response.substring(start + "SELECTED:".length()).trim();
        if (selection.startsWith("[") && selection.contains("]")) {
            String ids = selection.substring(selection.indexOf('[') + 1, selection.indexOf(']'));
            return java.util.Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(validIds::contains)
                .toList();
        }
        return Collections.emptyList();
    }

}
```

- [ ] **Step 2: 编写测试**

```java
class ProfessionalKbDecisionNodeTest {

    @Test
    void parseSelectionExtractsSingleKbId() {
        RagProperties properties = ragPropertiesWithKb("pg-guide", "tech-docs");
        ProfessionalKbDecisionNode node = new ProfessionalKbDecisionNode(
                ChatClient.builder(new StubChatModel("SELECTED: [pg-guide]")), properties);
        List<String> result = node.parseSelectionForTesting("SELECTED: [pg-guide]",
                properties.getProfessionalKnowledgeBases().getKnowledgeBases());

        assertThat(result).containsExactly("pg-guide");
    }

    @Test
    void parseSelectionExtractsMultipleKbIds() {
        RagProperties properties = ragPropertiesWithKb("pg-guide", "tech-docs", "medical-kb");
        ProfessionalKbDecisionNode node = new ProfessionalKbDecisionNode(
                ChatClient.builder(new StubChatModel("unused")), properties);
        List<String> result = node.parseSelectionForTesting("SELECTED: [pg-guide, medical-kb]",
                properties.getProfessionalKnowledgeBases().getKnowledgeBases());

        assertThat(result).containsExactly("pg-guide", "medical-kb");
    }

    @Test
    void parseSelectionReturnsEmptyForNoSelection() {
        RagProperties properties = ragPropertiesWithKb("pg-guide");
        ProfessionalKbDecisionNode node = new ProfessionalKbDecisionNode(
                ChatClient.builder(new StubChatModel("No knowledge base needed.")), properties);
        List<String> result = node.parseSelectionForTesting("No knowledge base needed.",
                properties.getProfessionalKnowledgeBases().getKnowledgeBases());

        assertThat(result).isEmpty();
    }

    @Test
    void parseSelectionReturnsEmptyForMalformedResponse() {
        RagProperties properties = ragPropertiesWithKb("pg-guide");
        ProfessionalKbDecisionNode node = new ProfessionalKbDecisionNode(
                ChatClient.builder(new StubChatModel("unused")), properties);
        List<String> result = node.parseSelectionForTesting("SELECTED: pg-guide",
                properties.getProfessionalKnowledgeBases().getKnowledgeBases());

        assertThat(result).isEmpty();
    }

    @Test
    void parseSelectionIgnoresUnknownKbIds() {
        RagProperties properties = ragPropertiesWithKb("pg-guide");
        ProfessionalKbDecisionNode node = new ProfessionalKbDecisionNode(
                ChatClient.builder(new StubChatModel("unused")), properties);
        List<String> result = node.parseSelectionForTesting("SELECTED: [pg-guide, unknown-kb]",
                properties.getProfessionalKnowledgeBases().getKnowledgeBases());

        assertThat(result).containsExactly("pg-guide");
    }

    private RagProperties ragPropertiesWithKb(String... kbIds) {
        RagProperties properties = new RagProperties();
        List<RagProperties.ProfessionalKnowledgeBases.KnowledgeBase> kbs = new ArrayList<>();
        for (String id : kbIds) {
            RagProperties.ProfessionalKnowledgeBases.KnowledgeBase kb =
                    new RagProperties.ProfessionalKnowledgeBases.KnowledgeBase();
            kb.setId(id);
            kb.setName(id + "-name");
            kb.setDescription("Description for " + id);
            kb.setEnabled(true);
            kbs.add(kb);
        }
        properties.getProfessionalKnowledgeBases().setKnowledgeBases(kbs);
        return properties;
    }

}
```

**注意**：需要在 `ProfessionalKbDecisionNode` 中将 `parseSelection` 方法设为 package-private（去掉 `private`），或添加 `parseSelectionForTesting` 测试辅助方法暴露 `parseSelection` 逻辑。

- [ ] **Step 2: 添加 parseSelectionForTesting 方法到 ProfessionalKbDecisionNode**

```java
/**
 * Exposed for testing only.
 */
List<String> parseSelectionForTesting(String response,
        List<RagProperties.ProfessionalKnowledgeBases.KnowledgeBase> enabledKbs) {
    return parseSelection(response, enabledKbs);
}
```

- [ ] **Step 3: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/node/ProfessionalKbDecisionNode.java src/test/java/top/lanshan/manmu/node/ProfessionalKbDecisionNodeTest.java && git commit -m "实现专业知识库决策节点（LLM 驱动的 KB 选择）"
```

### Task 4.3: ProfessionalKbRagNode 与 Graph 集成

**Files:**
- Create: `src/main/java/top/lanshan/manmu/node/ProfessionalKbRagNode.java`
- Modify: `src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`
- Test: `src/test/java/top/lanshan/manmu/node/ProfessionalKbRagNodeTest.java`

- [ ] **Step 1: 实现 ProfessionalKbRagNode**

与 `UserFileRagNode` 类似，但过滤条件使用 `source_type=professional_kb_es` + 固定的 `session_id=professional_kb_es`。节点名称为 `professional_kb_rag`，order 为 33。

```java
@Override
public String name() {
    return "professional_kb_rag";
}

@Override
public Flux<ResearchEvent> run(ResearchState state) {
    List<String> selectedKbs = state.selectedKnowledgeBases();
    if (selectedKbs == null || selectedKbs.isEmpty()) {
        return Flux.empty();
    }
    // 对每个选中的 KB 执行检索
    // source_type = "professional_kb_es", session_id = "professional_kb_es"
    // 对于 API 类型的 KB，调用外部 API（后续阶段实现）
}
```

- [ ] **Step 2: 在 ResearchGraphBuilder 添加 professional_kb_decision 和 professional_kb_rag 节点**

添加两个常量：
```java
public static final String PROFESSIONAL_KB_DECISION = "professional_kb_decision";
public static final String PROFESSIONAL_KB_RAG = "professional_kb_rag";
```

修改 `addAdvancedExecutionEdges`：在 `research_team` 之后添加 KB 决策路由：

```
research_team
  ├── [所有 step terminal] → reporter（现有）
  └── [有未完成 step] → parallel_executor（现有）
      但在此之前先经过 professional_kb_decision
```

实际上在 deepresearch-main 中，`professional_kb_decision` 位于 `research_team` 之后。为了更好地集成，将路由改为：

```
research_team → professional_kb_decision
  ├── [需要 KB] → professional_kb_rag → research_team（循环回）
  └── [不需要 KB 或 全部 terminal] → reporter
```

但为了最小化改动，我们可以将 `professional_kb_decision` 插入到 `research_team → parallel_executor / reporter` 之间：仅当所有 step terminal 时才经过 KB 决策。

修改后的路由逻辑在 `addAdvancedExecutionEdges` 中处理。

- [ ] **Step 3: 添加 ResearchState 字段**

在 `ResearchState.java` 中添加：

```java
private List<String> selectedKnowledgeBases = Collections.emptyList();

public List<String> selectedKnowledgeBases() { return selectedKnowledgeBases; }
public void selectedKnowledgeBases(List<String> selectedKnowledgeBases) {
    this.selectedKnowledgeBases = selectedKnowledgeBases == null ? Collections.emptyList() : selectedKnowledgeBases;
}
```

- [ ] **Step 4: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/node/ProfessionalKbRagNode.java src/main/java/top/lanshan/manmu/node/ProfessionalKbDecisionNode.java src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java src/main/java/top/lanshan/manmu/model/ResearchState.java && git commit -m "集成专业知识库检索节点到 Graph 工作流"
```

### Task 4.4: 真实 E2E 验证专业知识库检索

- [ ] **Step 1: 配置一个测试知识库**

在 `application.yml` 中添加：

```yaml
mvp:
  rag:
    enabled: true
    professional-knowledge-bases:
      decision-enabled: true
      knowledge-bases:
        - id: "postgresql-guide"
          name: "PostgreSQL Performance Guide"
          description: "Best practices for PostgreSQL performance tuning, indexing, and scaling"
          type: "elasticsearch"
          priority: 10
          enabled: true
```

- [ ] **Step 2: 上传知识库文档**

```bash
curl -s -X POST http://localhost:18080/api/rag/upload \
  -F "file=@C:/MainData/code/Claude_project/M-agent/target/http-check/rag-pg-guide.md" \
  -F "session_id=professional_kb_es" \
  -F "user_id=system" 2>&1
```

- [ ] **Step 3: 运行研究请求**

```bash
echo '{"query":"What is the best way to index data in PostgreSQL?","thread_id":"rag-stage4","auto_accepted_plan":true}' > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-stage4-request.json

curl -s -N -X POST http://localhost:18080/chat/stream \
  -H "Content-Type: application/json" \
  --data-binary "@C:/MainData/code/Claude_project/M-agent/target/http-check/rag-stage4-request.json" \
  --max-time 300 > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-stage4-result.sse 2>&1
```

- [ ] **Step 4: 验证 KB 决策和检索**

```bash
grep -c "professional_kb_decision" C:/MainData/code/Claude_project/M-agent/target/http-check/rag-stage4-result.sse
# Expected: >0
grep -c "professional_kb_rag" C:/MainData/code/Claude_project/M-agent/target/http-check/rag-stage4-result.sse
# Expected: >0（如果 LLM 决策需要 KB）
```

- [ ] **Step 5: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add -A && git commit -m "阶段 4 完成：专业知识库决策与检索 + E2E 验证"
```

---

## 阶段 5：集成收尾

> **目标**：全量测试通过、全链路 E2E 覆盖所有 RAG 路径、代码审查、handoff 文档、中文 commit。

### Task 5.1: 全量 Maven 测试

**Files:** 所有

- [ ] **Step 1: 运行全量测试**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B test 2>&1 | tail -10
# Expected: Tests run: N, Failures: 0, Errors: 0, BUILD SUCCESS
```

### Task 5.2: 完整 E2E 覆盖

- [ ] **Step 1: 启动后端并运行以下测试场景**

```
场景 A: rag.enabled=true, 上传文件 → /chat/stream → 报告包含文件内容
场景 B: rag.enabled=true, 不传文件 → /chat/stream → 正常运行（user_file_rag 透明通过）
场景 C: rag.enabled=false → /chat/stream → 正常运行（RAG 完全不参与）
场景 D: rag.enabled=true, 配置 KB → /chat/stream → 报告包含 KB 内容
场景 E: rag.enabled=true, 手动暂停 + 恢复 → 正常暂停/恢复
场景 F: /api/rag/upload → 验证 ingestion API
```

- [ ] **Step 2: 全链路验证通过后关闭服务**

### Task 5.3: 代码审查与收尾

- [ ] **Step 1: 扫描死代码和旧引用**

```bash
grep -rn "ProcessorNode\|RESEARCHER\|PROCESSOR\|isEnabled" src/main/ src/test/ --include="*.java"
# Expected: 无不期待的旧路径引用（isEnabled 应该在 RagProperties 中出现）
```

- [ ] **Step 2: 创建 handoff 文件**

写 `.codex/tasks/rag-implementation.md` 记录阶段 1-5 完成状态、E2E 验证结果、后续扩展点。

- [ ] **Step 3: 最终提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add -A && git commit -m "RAG 检索增强生成实现完成"
```

---

## 风险与控制

| 风险 | 缓解措施 |
|------|---------|
| pgvector 扩展在 PostgreSQL 17 的兼容性 | pgvector 官方支持 PG 12-17；Flyway 迁移中加 `IF NOT EXISTS` |
| DashScope embedding 模型限流 | 同现有 chat model 限流策略；必要时切换 provider |
| Tika 对大文件的内存占用 | TokenTextSplitter 限制 `maxNumChunks=100`；上传文件大小限制后续添加 |
| RAG 检索噪声干扰研究质量 | `rag.md` prompt 强调「严格忠于原文」「信息不足时明确声明」 |
| Embedding 向量维度不匹配 | 硬编码 1536（DashScope text-embedding-v1），在配置中声明 |
