# RAG 向量相似度搜索返回 0 结果 — 根因修复

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 PgVectorStore 相似度搜索始终返回 0 条结果的 bug，确保 RAG 检索能命中相关文档。

**Architecture:** 根因在 Spring AI 1.0.0 的 `PgVectorStore.doSimilaritySearch()` 内部对 threshold 做了 `1.0 - similarityThreshold` 的减法转换，导致默认配置值 `0.7` 的实际生效阈值为 `0.3`（非常严格）。将 `mvp.rag.similarity-threshold` 默认值从 `0.7` 改为 `0.3`，同时降低 `application.yml` 默认值并添加日志辅助调试。

**Tech Stack:** Java 17, Spring AI 1.0.0, PgVectorStore COSINE_DISTANCE

---

## 根因分析

通过反编译 `PgVectorStore.doSimilaritySearch()` 字节码发现（`spring-ai-pgvector-store-1.0.0.jar`）：

```
 45: dconst_1              // push 1.0
 46: aload_1
 47: invokevirtual #325    // SearchRequest.getSimilarityThreshold()
 50: dsub                  // 1.0 - similarityThreshold → effectiveThreshold
 51: dstore 4              // store effectiveThreshold
```

SQL 模板（COSINE_DISTANCE）：
```sql
SELECT *, embedding <=> ? AS distance 
FROM %s 
WHERE embedding <=> ? < ? %s 
ORDER BY distance 
LIMIT ?
```

第 3 个 `?` 参数就是 `effectiveThreshold = 1.0 - similarityThreshold`。

**转换关系：**

| 配置值 `similarity-threshold` | 实际 SQL 阈值 `1.0 - config` | 含义 (cosine distance) |
|------|------|------|
| 0.0  | 1.0  | 接受所有向量 |
| 0.3  | 0.7  | 接受 cosine_similarity > 0.65 的文档 ← **推荐** |
| 0.5  | 0.5  | 接受 cosine_similarity > 0.75 的文档 |
| 0.7  | 0.3  | 接受 cosine_similarity > 0.85 的文档（太严格） |
| 1.0  | 0.0  | 仅接受完全相同的向量（不可能） |

**结论：** 默认值 `0.7` 导致实际阈值 `0.3`，过滤掉了所有正常文档。需要降低配置值。

---

## 文件结构总览

| 层 | 文件 | 操作 |
|---|------|------|
| 配置 | `application.yml` | 修改 `mvp.rag.similarity-threshold` 默认值 |
| 检索 | `RagRetriever.java` | 添加日志输出实际 threshold 值 |
| 测试 | `RagRetrieverTest.java` | 更新 buildContext 测试 |

---

### Task 1: 修复 similarity-threshold 默认值

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/top/lanshan/manmu/config/RagProperties.java`
- Modify: `src/main/java/top/lanshan/manmu/rag/RagRetriever.java`
- Modify: `src/test/java/top/lanshan/manmu/rag/RagRetrieverTest.java`

- [ ] **Step 1: 修改默认值（application.yml + RagProperties.java）**

改两个地方的默认值，确保即使配置文件缺失也使用正确的默认值。

**1a. 修改 `src/main/resources/application.yml` 第 43 行：**

```yaml
    similarity-threshold: 0.3
```

**1b. 修改 `src/main/java/top/lanshan/manmu/config/RagProperties.java` 第 15 行：**

```java
private double similarityThreshold = 0.3;
```

注意：`0.3` 在 PgVectorStore 内部转换为 `1.0 - 0.3 = 0.7`，对应 cosine_similarity > 0.65，这是合理的检索精度。原值 `0.7` 会转换为 `1.0 - 0.7 = 0.3`，过于严格。

- [ ] **Step 2: 在 RagRetriever 中添加 threshold 日志**

修改 `src/main/java/top/lanshan/manmu/rag/RagRetriever.java`，在 `retrieve()` 方法的 logger.info 中增加 threshold 信息。

当前代码（约第 40 行）：
```java
logger.info("Retrieved {} documents (filtered to {}) for query: {}", results.size(), filtered.size(),
        query.length() > 100 ? query.substring(0, 100) + "..." : query);
```

改为：
```java
logger.info("Retrieved {} docs (filtered to {}) [threshold={}, topK={}] for query: {}",
        results.size(), filtered.size(), similarityThreshold, topK,
        query.length() > 100 ? query.substring(0, 100) + "..." : query);
```

- [ ] **Step 3: 更新 RagRetrieverTest 中的 buildContext 测试**

检查 `src/test/java/top/lanshan/manmu/rag/RagRetrieverTest.java`，确认现有测试仍能通过。阈值变更不影响 buildContext 测试。

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B "-Dtest=RagRetrieverTest" test 2>&1 | tail -5
# Expected: Tests run: 2, Failures: 0, BUILD SUCCESS
```

- [ ] **Step 4: 编译验证**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B compile 2>&1 | tail -5
# Expected: BUILD SUCCESS
```

- [ ] **Step 5: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/resources/application.yml src/main/java/top/lanshan/manmu/config/RagProperties.java src/main/java/top/lanshan/manmu/rag/RagRetriever.java && git commit -m "修复相似度阈值默认值：0.7→0.3（PgVectorStore 内部做 1.0-threshold 转换）"
```

---

### Task 2: 真实环境 E2E 验证

> **目标：** 启动后端 → 上传文件 → 发送研究请求 → 确认 user_file_rag 节点输出了 RAG 检索结果。

- [ ] **Step 1: 确保 PostgreSQL 运行**

```bash
docker ps --filter name=manmu-postgres --format "{{.Names}} {{.Status}}"
# Expected: manmu-postgres Up ...
```

- [ ] **Step 2: 确保 rag_vectors 表存在且 pgvector 扩展已安装**

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "\d rag_vectors"
# Expected: 显示 id, content, metadata, embedding 四列

docker exec manmu-postgres psql -U manmu -d manmu -c "SELECT * FROM pg_extension WHERE extname='vector';"
# Expected: 返回 1 行，extname=vector
```

如果 `rag_vectors` 表不存在，手动创建：
```sql
CREATE TABLE IF NOT EXISTS public.rag_vectors (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    content text,
    metadata jsonb,
    embedding vector(1536),
    PRIMARY KEY (id)
);
```

- [ ] **Step 3: 启动后端（RAG 启用，threshold=0.3）**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B spring-boot:run -Dspring-boot.run.profiles=real-model "-Dspring-boot.run.arguments=--server.port=18080 --mvp.rag.enabled=true" > target/rag-e2e-fix.log 2>&1 &
sleep 25
grep "Started DeepResearchMvpApplication" target/rag-e2e-fix.log
# Expected: Started DeepResearchMvpApplication
```

- [ ] **Step 4: 确认 EmbeddingModel 已创建**

```bash
grep "Creating DashScope embedding model" target/rag-e2e-fix.log
# Expected: Creating DashScope embedding model for RAG (model: text-embedding-v1)
```

- [ ] **Step 5: 上传测试文件**

```bash
echo "# PostgreSQL Performance Guide\n\nPostgreSQL can handle large datasets efficiently with proper indexing. B-tree indexes are the default, but GIN indexes excel at full-text search. For vector search, pgvector uses IVFFlat indexes that dramatically speed up similarity queries.\n\nPartitioning tables by date range improves query performance when scanning recent data." > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-fix-test.md

curl.exe -s -X POST http://localhost:18080/api/rag/upload \
  -F "file=@C:/MainData/code/Claude_project/M-agent/target/http-check/rag-fix-test.md" \
  -F "session_id=rag-fix-session" \
  -F "user_id=test-user" 2>&1
# Expected: {"thread_id":"rag-fix-session","status":"success","message":"File ingested successfully",...}
```

- [ ] **Step 6: 验证向量存储有数据**

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "SELECT COUNT(*) FROM rag_vectors WHERE metadata->>'session_id' = 'rag-fix-session';"
# Expected: count >= 1
```

- [ ] **Step 7: 发送研究请求并验证 RAG 节点输出**

```bash
echo '{"query":"How to index data in PostgreSQL?","thread_id":"rag-fix-thread","session_id":"rag-fix-session","auto_accepted_plan":true}' > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-fix-request.json

curl.exe -s -N -X POST http://localhost:18080/chat/stream \
  -H "Content-Type: application/json" \
  --data-binary "@C:/MainData/code/Claude_project/M-agent/target/http-check/rag-fix-request.json" \
  --max-time 300 > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-fix-result.sse 2>&1
```

等待研究完成后（约 30-60 秒），验证：

```bash
# 验证 user_file_rag 节点有输出
grep -c "user_file_rag" C:/MainData/code/Claude_project/M-agent/target/http-check/rag-fix-result.sse
# Expected: > 0

# 验证日志中有非零检索结果
grep "Retrieved.*docs.*filtered" C:/MainData/code/Claude_project/M-agent/target/rag-e2e-fix.log
# Expected: Retrieved 1 docs (filtered to 1) [threshold=0.3, topK=5] ...

# 验证研究成功完成
grep '"done":true.*graph.completed' C:/MainData/code/Claude_project/M-agent/target/http-check/rag-fix-result.sse
# Expected: 有匹配
```

- [ ] **Step 8: 关闭后端并确认端口释放**

```bash
powershell -Command "Stop-Process -Id (Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue).OwningProcess -Force" 2>/dev/null
sleep 2
powershell -Command "Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue"
# Expected: 无输出（端口已释放）
```

---

### Task 3: 集成收尾

- [ ] **Step 1: 运行全量测试**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B test 2>&1 | grep -E "Tests run:.*Failures|BUILD" | tail -5
# Expected: Tests run: 133, Failures: 0, Errors: 12 (12 个为 PostgreSQL 预存错误)
```

- [ ] **Step 2: 提交 E2E 验证结果**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add -A && git commit -m "RAG 相似度搜索修复完成：阈值 0.3 + E2E 验证通过"
```

---

## 风险与控制

| 风险 | 缓解措施 |
|------|---------|
| `similarity-threshold=0.3` 返回过多不相关文档（噪声） | 对应 cosine_similarity > 0.65，实际检索精度合理；如噪声过大可调至 0.4（实际 0.6） |
| PgVectorStore 版本升级后 `1.0 - threshold` 行为改变 | 当前锁定 `spring-ai-pgvector-store:1.0.0`，升级时验证 |
| 不同 Embedding 模型（DashScope vs 其他）产生的语义相似度分布不同 | 当前仅使用 DashScope text-embedding-v1，后续支持其他模型时需分别调优 |
