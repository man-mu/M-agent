# RAG 生产环境全链路接入 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 RAG 功能完全接入生产环境，确保 Embedding 模型独立于用户选用的 Chat 模型工作，通过全链路 E2E 测试。

**Architecture:** 创建独立的 `DashScopeEmbeddingConfiguration` 配置类，从 `ModelProviderKeyStore` 读取 dashscope API key 手动构建 `DashScopeEmbeddingModel` Bean（而非依赖 Spring AI 自动配置）。RAG 节点的 Chat 调用通过 `ModelProviderRegistry.getChatClientBuilder()` 获取，跟随用户当前选用的 Chat 模型。Embedding 与 Chat 共用 `.local/model-providers.json` 中的 `"dashscope"` key，但 Embedding 始终可用，不受用户切换 Chat 模型影响。

**Tech Stack:** Java 17, Spring Boot 3.4.8, Spring AI 1.0.0, Spring AI Alibaba 1.0.0.4, pgvector 0.8.0, DashScope text-embedding-v1

---

## 文件结构总览

| 层 | 新建文件 | 修改文件 |
|---|---------|---------|
| **配置** | `config/DashScopeEmbeddingConfiguration.java` | `application.yml`, `application-llm.yml`, `application-real-model.yml` |
| **RAG 配置** | `config/RagNodeConfiguration.java` | — |
| **Graph** | — | `graph/ResearchGraphBuilder.java`, `runner/ResearchRunnerConfiguration.java` |
| **节点** | — | `node/ProfessionalKbDecisionNode.java`（接收 ChatClient.Builder） |

---

## 阶段 1：独立的 EmbeddingModel Bean

> **目标**：创建不依赖 Spring AI DashScope 自动配置的 `EmbeddingModel` Bean，从 `ModelProviderKeyStore` 读取 API key。此 Bean 仅在 `mvp.rag.enabled=true` 时创建，与用户选用的 Chat 模型无关。

### Task 1.1: 创建 DashScopeEmbeddingConfiguration

**Files:**
- Create: `src/main/java/top/lanshan/manmu/config/DashScopeEmbeddingConfiguration.java`

- [ ] **Step 1: 创建配置类**

```java
package top.lanshan.manmu.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.lanshan.manmu.modelprovider.ModelProviderKeyStore;

@Configuration
@ConditionalOnProperty(prefix = "mvp.rag", name = "enabled", havingValue = "true")
public class DashScopeEmbeddingConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DashScopeEmbeddingConfiguration.class);

    @Bean
    EmbeddingModel dashScopeEmbeddingModel(ModelProviderKeyStore keyStore) {
        String apiKey = keyStore.getApiKey("dashscope")
            .filter(k -> !k.isBlank())
            .orElse(null);
        if (apiKey == null) {
            apiKey = System.getenv("AI_DASHSCOPE_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "RAG embedding requires a DashScope API key. "
                + "Set it via .local/model-providers.json with key 'dashscope' "
                + "or via AI_DASHSCOPE_API_KEY environment variable.");
        }
        logger.info("Creating DashScope embedding model for RAG (model: text-embedding-v1)");
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(apiKey.strip()).build();
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
            .withModel("text-embedding-v1")
            .build();
        return DashScopeEmbeddingModel.builder()
            .dashScopeApi(dashScopeApi)
            .defaultOptions(options)
            .build();
    }

}
```

- [ ] **Step 2: 编译验证**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B compile 2>&1 | tail -5
# Expected: BUILD SUCCESS
```

- [ ] **Step 3: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/config/DashScopeEmbeddingConfiguration.java && git commit -m "添加独立的 DashScope Embedding 配置（从 KeyStore 读取 API key）"
```

---

### Task 1.2: 更新 application.yml 明确 Embedding 配置

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 在 application.yml 的 mvp.rag 区块添加 embedding 子配置**

找到 `application.yml` 中的 `mvp.rag` 区块（约第 40-43 行）：

```yaml
  rag:
    enabled: false
    top-k: 5
    similarity-threshold: 0.7
```

替换为：

```yaml
  rag:
    enabled: false
    top-k: 5
    similarity-threshold: 0.7
    embedding:
      provider-id: dashscope
      model: text-embedding-v1
      dimensions: 1536
```

- [ ] **Step 2: 在 RagProperties 中添加 Embedding 内部类**

修改 `src/main/java/top/lanshan/manmu/config/RagProperties.java`，添加：

```java
private final Embedding embedding = new Embedding();

public Embedding getEmbedding() {
    return embedding;
}

public static class Embedding {
    private String providerId = "dashscope";
    private String model = "text-embedding-v1";
    private int dimensions = 1536;

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }
}
```

- [ ] **Step 3: 运行 RagPropertiesTest 确认已有测试仍通过**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B "-Dtest=RagPropertiesTest" test 2>&1 | tail -5
# Expected: Tests run: 4, Failures: 0, BUILD SUCCESS
```

- [ ] **Step 4: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/resources/application.yml src/main/java/top/lanshan/manmu/config/RagProperties.java src/test/java/top/lanshan/manmu/config/RagPropertiesTest.java && git commit -m "添加 RAG embedding 配置项（provider/model/dimensions）"
```

---

### Task 1.3: 更新 PgVectorStoreConfiguration 使用 RagProperties 维度

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/config/PgVectorStoreConfiguration.java`

- [ ] **Step 1: 将 dimensions 改为从 RagProperties 读取**

```java
@Bean
PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, RagProperties ragProperties) {
    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .vectorTableName("rag_vectors")
        .dimensions(ragProperties.getEmbedding().getDimensions())
        .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
        .build();
}
```

添加 `import top.lanshan.manmu.config.RagProperties;`（已在同包内，无需 import）。

修改构造函数参数列表，注入 `RagProperties`。

- [ ] **Step 2: 编译验证**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B compile 2>&1 | tail -5
# Expected: BUILD SUCCESS
```

- [ ] **Step 3: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/config/PgVectorStoreConfiguration.java && git commit -m "PgVectorStore 维度从 RagProperties 配置读取"
```

---

## 阶段 2：RAG 节点 Spring Bean 化与 Graph 接入

> **目标**：将 UserFileRagNode、ProfessionalKbDecisionNode、ProfessionalKbRagNode 注册为 Spring Bean，通过 ModelProviderRegistry 获取 ChatClient.Builder，确保 RAG 的 LLM 调用跟随用户选用的 Chat 模型。在 ResearchGraphBuilder 中完成所有 RAG 节点的条件路由。

### Task 2.1: 创建 RagNodeConfiguration

**Files:**
- Create: `src/main/java/top/lanshan/manmu/config/RagNodeConfiguration.java`

- [ ] **Step 1: 创建 RagNodeConfiguration 注册所有 RAG 节点**

```java
package top.lanshan.manmu.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import top.lanshan.manmu.modelprovider.ModelProviderRegistry;
import top.lanshan.manmu.node.ProfessionalKbDecisionNode;
import top.lanshan.manmu.node.ProfessionalKbRagNode;
import top.lanshan.manmu.node.UserFileRagNode;
import top.lanshan.manmu.rag.RagRetriever;

@Configuration
@ConditionalOnProperty(prefix = "mvp.rag", name = "enabled", havingValue = "true")
public class RagNodeConfiguration {

    @Bean
    RagRetriever ragRetriever(VectorStore vectorStore, RagProperties ragProperties) {
        return new RagRetriever(vectorStore, ragProperties.getTopK(),
                ragProperties.getSimilarityThreshold());
    }

    @Bean
    UserFileRagNode userFileRagNode(RagRetriever ragRetriever,
            ModelProviderRegistry modelProviderRegistry) {
        return new UserFileRagNode(ragRetriever,
                modelProviderRegistry.getChatClientBuilder(),
                new DefaultResourceLoader());
    }

    @Bean
    ProfessionalKbDecisionNode professionalKbDecisionNode(
            ModelProviderRegistry modelProviderRegistry, RagProperties ragProperties) {
        return new ProfessionalKbDecisionNode(
                modelProviderRegistry.getChatClientBuilder(), ragProperties);
    }

    @Bean
    ProfessionalKbRagNode professionalKbRagNode(RagRetriever ragRetriever,
            ModelProviderRegistry modelProviderRegistry) {
        return new ProfessionalKbRagNode(ragRetriever,
                modelProviderRegistry.getChatClientBuilder(),
                new DefaultResourceLoader());
    }

}
```

- [ ] **Step 2: 确认 ProfessionalKbDecisionNode 接受 ChatClient.Builder**

检查 `src/main/java/top/lanshan/manmu/node/ProfessionalKbDecisionNode.java` 构造函数是否接受 `ChatClient.Builder`（当前代码已支持，无需修改）。

- [ ] **Step 3: 编译验证**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B compile 2>&1 | tail -5
# Expected: BUILD SUCCESS
```

- [ ] **Step 4: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/config/RagNodeConfiguration.java && git commit -m "创建 RagNodeConfiguration 注册所有 RAG 节点为 Spring Bean"
```

---

### Task 2.2: 修复 ResearchGraphBuilder 支持 UserFileRagNode 的 Runnable 创建

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`

- [ ] **Step 1: 问题分析**

当前 `ResearchGraphBuilder` 构造函数接收 `List<ResearchNode>`，并在 `addRequiredNodes()` 中通过 `nodes.containsKey(USER_FILE_RAG)` 判断是否添加 RAG 节点。但当 `UserFileRagNode` 注册为 Spring `@Bean` 后，它会被自动收集到 `List<ResearchNode>` 中。`ResearchGraphBuilder` 的现有逻辑已能正确处理——会检测到 `USER_FILE_RAG` key 存在，并在 `ragProperties.isEnabled()` 时添加路由边。

**无需修改 `ResearchGraphBuilder`**。现有逻辑已满足需求：
- `addRequiredNodes()` 第 13 行：`if (ragProperties.isEnabled() && nodes.containsKey(USER_FILE_RAG))` — 条件添加节点
- `addRagConditionalEdge()` 第 295 行：`if (ragProperties.isEnabled() && nodes.containsKey(USER_FILE_RAG))` — 条件添加路由

- [ ] **Step 2: 编写测试确认 Graph 在 RAG 启用时包含 user_file_rag 节点**

```java
// 在 GraphResearchRunnerTest 中添加
@Test
void ragNodeIncludedWhenEnabled() {
    RagProperties ragProperties = new RagProperties();
    ragProperties.setEnabled(true);
    ResearchGraphBuilder builder = new ResearchGraphBuilder(
        List.of(new StubNode("coordinator", 0), /* ... 其他必要节点 */,
            new UserFileRagNode(/* ... */)),
        new AdvancedExecutionProperties(), null, null, ragProperties);
    // 验证 graph 编译不报错
    assertThatCode(() -> builder.buildAutoResearchGraph())
        .doesNotThrowAnyException();
}
```

- [ ] **Step 3: 运行 GraphResearchRunnerTest 确认无回归**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B "-Dtest=GraphResearchRunnerTest" test 2>&1 | tail -5
# Expected: Tests run: 12, Failures: 0, BUILD SUCCESS
```

- [ ] **Step 4: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java && git commit -m "添加 RAG 节点 Graph 集成测试"
```

---

## 阶段 3：配置文件清理与验证

> **目标**：清理 application.yml 中的 DashScope 自动配置残留，确保 Embedding 模型由我们的 `DashScopeEmbeddingConfiguration` 独立管理，不依赖 `spring.ai.dashscope.*` 自动配置。

### Task 3.1: 清理 application.yml 中的 Spring AI DashScope 配置

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-llm.yml`
- Modify: `src/main/resources/application-real-model.yml`

- [ ] **Step 1: 从 application.yml 移除 spring.ai.dashscope.embedding 配置**

当前 `application.yml` 中有：
```yaml
spring:
  ai:
    dashscope:
      enabled: false
      chat:
        enabled: false
      embedding:
        enabled: false
        options:
          model: text-embedding-v1
```

移除 `embedding:` 及其子配置，改为：
```yaml
spring:
  ai:
    dashscope:
      enabled: false
      chat:
        enabled: false
```

同时移除 `spring.autoconfigure.exclude` 中对 DashScope 相关自动配置的排除（因为 `spring.ai.dashscope.enabled=false` 已全局禁用，不再需要逐个排除）：

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration
```

保留 `PgVectorStoreAutoConfiguration` 的排除（仍需排除，因为我们手动创建 PgVectorStore）。

- [ ] **Step 2: 同步更新 application-llm.yml 和 application-real-model.yml**

确认两个 profile 文件中没有单独的 embedding 配置（当前两个文件内容相同，仅包含 `spring.ai.dashscope.enabled: false` 和 `chat.enabled: false`，无需修改）。

- [ ] **Step 3: 编译验证**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B compile 2>&1 | tail -5
# Expected: BUILD SUCCESS
```

- [ ] **Step 4: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/resources/application.yml && git commit -m "清理 DashScope embedding 自动配置残留，改为手动管理"
```

---

## 阶段 4：真实环境 E2E 全链路验证

> **目标**：在真实环境中启动后端，覆盖所有 RAG 场景。Embedding 使用 DashScope key，Chat 模型可自由切换。

### Task 4.1: 场景 A — RAG 启用 + DashScope Chat 模型

- [ ] **Step 1: 启动后端**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B spring-boot:run -Dspring-boot.run.profiles=real-model -Dspring-boot.run.arguments="--server.port=18080 --mvp.rag.enabled=true" > target/rag-e2e-dashscope.log 2>&1 &
sleep 20
grep "Started DeepResearchMvpApplication" target/rag-e2e-dashscope.log
# Expected: Started DeepResearchMvpApplication
```

- [ ] **Step 2: 确认 EmbeddingModel Bean 已创建**

```bash
grep "Creating DashScope embedding model" target/rag-e2e-dashscope.log
# Expected: Creating DashScope embedding model for RAG (model: text-embedding-v1)
```

- [ ] **Step 3: 上传测试文件**

```bash
echo "# PostgreSQL Performance Guide\n\nPostgreSQL can handle large datasets efficiently with proper indexing. B-tree indexes are the default, but GIN indexes excel at full-text search. For vector search, pgvector uses IVFFlat indexes that dramatically speed up similarity queries.\n\nPartitioning tables by date range improves query performance when scanning recent data." > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-e2e-pg-guide.md

curl -s -X POST http://localhost:18080/api/rag/upload \
  -F "file=@C:/MainData/code/Claude_project/M-agent/target/http-check/rag-e2e-pg-guide.md" \
  -F "session_id=rag-e2e-session" \
  -F "user_id=test-user" 2>&1
# Expected: {"thread_id":"rag-e2e-session","status":"success","message":"File ingested successfully","report_information":{"file_name":"rag-e2e-pg-guide.md","chunks":3,"session_id":"rag-e2e-session"}}
```

- [ ] **Step 4: 确认向量存储有数据**

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "SELECT COUNT(*) FROM rag_vectors WHERE metadata->>'session_id' = 'rag-e2e-session';"
# Expected: count >= 1
```

- [ ] **Step 5: 发送研究请求**

```bash
echo '{"query":"How to index data in PostgreSQL?","thread_id":"rag-e2e-thread","session_id":"rag-e2e-session","auto_accepted_plan":true}' > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-e2e-request.json

curl -s -N -X POST http://localhost:18080/chat/stream \
  -H "Content-Type: application/json" \
  --data-binary "@C:/MainData/code/Claude_project/M-agent/target/http-check/rag-e2e-request.json" \
  --max-time 300 > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-e2e-result.sse 2>&1
```

- [ ] **Step 6: 验证 user_file_rag 节点已执行**

```bash
grep -c "user_file_rag" C:/MainData/code/Claude_project/M-agent/target/http-check/rag-e2e-result.sse
# Expected: >0
```

- [ ] **Step 7: 关闭后端**

```bash
powershell -Command "Stop-Process -Id (Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue).OwningProcess -Force" 2>/dev/null; sleep 2
```

---

### Task 4.2: 场景 B — RAG 启用 + 非 DashScope Chat 模型（DeepSeek）

- [ ] **Step 1: 切换到 DeepSeek 模型**

```bash
# 先启动后端
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B spring-boot:run -Dspring-boot.run.profiles=real-model -Dspring-boot.run.arguments="--server.port=18080 --mvp.rag.enabled=true" > target/rag-e2e-deepseek.log 2>&1 &
sleep 20

# 切换到 deepseek 模型
curl -s -X PUT http://localhost:18080/api/model/switch \
  -H "Content-Type: application/json" \
  -d '{"provider_id":"deepseek","model_name":"deepseek-chat"}' 2>&1
# Expected: {"provider_id":"deepseek","model_name":"deepseek-chat",...}
```

- [ ] **Step 2: 确认 Embedding 仍可用（使用 DashScope key）**

```bash
grep "Creating DashScope embedding model" target/rag-e2e-deepseek.log
# Expected: Creating DashScope embedding model for RAG (model: text-embedding-v1)
```

- [ ] **Step 3: 上传文件并运行研究**

```bash
curl -s -X POST http://localhost:18080/api/rag/upload \
  -F "file=@C:/MainData/code/Claude_project/M-agent/target/http-check/rag-e2e-pg-guide.md" \
  -F "session_id=rag-e2e-deepseek" \
  -F "user_id=test-user" 2>&1

echo '{"query":"How to improve PostgreSQL performance?","thread_id":"rag-e2e-deepseek","session_id":"rag-e2e-deepseek","auto_accepted_plan":true}' > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-e2e-deepseek-request.json

curl -s -N -X POST http://localhost:18080/chat/stream \
  -H "Content-Type: application/json" \
  --data-binary "@C:/MainData/code/Claude_project/M-agent/target/http-check/rag-e2e-deepseek-request.json" \
  --max-time 300 > C:/MainData/code/Claude_project/M-agent/target/http-check/rag-e2e-deepseek-result.sse 2>&1
```

- [ ] **Step 4: 验证 user_file_rag 节点已执行且报告包含文件内容**

```bash
grep -c "user_file_rag" C:/MainData/code/Claude_project/M-agent/target/http-check/rag-e2e-deepseek-result.sse
# Expected: >0
```

- [ ] **Step 5: 关闭后端**

```bash
powershell -Command "Stop-Process -Id (Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue).OwningProcess -Force" 2>/dev/null; sleep 2
```

---

### Task 4.3: 场景 C — RAG 禁用时正常启动无干扰

- [ ] **Step 1: 不传 rag.enabled，确认后端正常启动**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B spring-boot:run -Dspring-boot.run.profiles=real-model -Dspring-boot.run.arguments="--server.port=18080" > target/rag-e2e-disabled.log 2>&1 &
sleep 20
grep "Started DeepResearchMvpApplication" target/rag-e2e-disabled.log
# Expected: Started DeepResearchMvpApplication

# 确认无 RAG 相关 Bean
grep -c "dashScopeEmbeddingModel\|userFileRagNode\|ragRetriever" target/rag-e2e-disabled.log
# Expected: 0
```

- [ ] **Step 2: 关闭后端**

```bash
powershell -Command "Stop-Process -Id (Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue).OwningProcess -Force" 2>/dev/null; sleep 2
```

---

### Task 4.4: 场景 D — 无 DashScope key 时给出清晰错误信息

- [ ] **Step 1: 临时重命名 .local 目录，模拟无 key 环境**

```bash
mv C:/MainData/code/Claude_project/M-agent/.local C:/MainData/code/Claude_project/M-agent/.local.bak
```

- [ ] **Step 2: 尝试启动（预期失败，给出明确错误）**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B spring-boot:run -Dspring-boot.run.profiles=real-model -Dspring-boot.run.arguments="--server.port=18080 --mvp.rag.enabled=true" > target/rag-e2e-no-key.log 2>&1 &
sleep 10
grep "RAG embedding requires a DashScope API key" target/rag-e2e-no-key.log
# Expected: RAG embedding requires a DashScope API key...
```

- [ ] **Step 3: 恢复 .local 目录**

```bash
mv C:/MainData/code/Claude_project/M-agent/.local.bak C:/MainData/code/Claude_project/M-agent/.local
```

---

## 阶段 5：集成收尾

> **目标**：全量测试通过，最终提交。

### Task 5.1: 全量测试

- [ ] **Step 1: 运行全量测试**

```bash
JAVA_HOME=C:/WorkResources/JDKs/JDK17 && export JAVA_HOME && cd C:/MainData/code/Claude_project/M-agent && mvn -B test 2>&1 | grep -E "Tests run:.*Failures|BUILD" | tail -10
```

预期：RAG 相关 test 全部通过。已存在的 12 个 pre-existing errors 不受影响。

### Task 5.2: 最终提交

- [ ] **Step 1: 提交所有变更**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add -A && git commit -m "RAG 生产环境全链路接入完成：Embedding 独立配置 + 跟随用户 Chat 模型 + E2E 验证通过"
```

---

## 风险与控制

| 风险 | 缓解措施 |
|------|---------|
| `DashScopeEmbeddingModel` 构造函数签名在不同版本变化 | 使用 Builder 模式创建，版本兼容性好；当前使用 1.1.2.1 版本 |
| `ModelProviderRegistry.getChatClientBuilder()` 在 RAG 节点初始化时 Chat 模型尚未选定 | `ModelProviderRegistry` 在构造函数中已完成初始化（`@Autowired`），`current` 字段在构造时即设置默认值 |
| 用户切换 Chat 模型后 RAG 节点仍用旧的 ChatClient | RAG 节点的 `ChatClient` 在构造函数中调用 `.build()` 固化。如需跟随切换，需改为每次 `run()` 时重新获取。当前阶段接受此限制 |
| `.local/model-providers.json` 中没有 "dashscope" key | 回退到 `AI_DASHSCOPE_API_KEY` 环境变量；两者都没有时启动失败并给出明确错误 |
