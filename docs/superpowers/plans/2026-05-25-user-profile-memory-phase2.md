# 用户角色记忆 — Phase 2 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从对话历史中提取用户角色摘要（如"Python初学者"），注入 Coordinator prompt 使后续研究更贴合用户背景。

**Architecture:** 新建 `user_profiles` 表通过 R2DBC 存储每个 session 的用户角色摘要。`UserProfileService` 读取 Phase 1 的 `conversation_messages` 表获取最近用户消息，调用 LLM 提取一句角色描述，存入 `user_profiles` 表，并在 Coordinator 决策时注入 prompt。配置通过 `mvp.memory.user-profile` 前缀控制。

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring Data R2DBC, PostgreSQL 17, Flyway

---

## 关键设计决策

| 维度 | 决定 |
|------|------|
| 存储 | PostgreSQL `user_profiles` 表（R2DBC），每个 session 保留最新一条 |
| 主键 | UUID（与 `conversation_messages` 一致） |
| 提取频率 | 每次研究请求开始时提取（有最近 1 小时内 profile 则跳过） |
| 提取方式 | LLM 调用（复用 `AgentClient`），内联 prompt 不单独建文件 |
| 注入位置 | `CoordinatorNode` → `LlmCoordinatorAgent.coordinate()` 的 user prompt |
| 开关 | `mvp.memory.user-profile.enabled`（默认 true） |

## 文件结构总览

| 层 | 文件 | 操作 |
|---|------|------|
| 迁移 | `src/main/resources/db/migration/V5__create_user_profiles.sql` | 创建 |
| Record | `src/main/java/top/lanshan/manmu/memory/UserProfileRecord.java` | 创建 |
| 实体 | `src/main/java/top/lanshan/manmu/memory/UserProfileEntity.java` | 创建 |
| 仓库 | `src/main/java/top/lanshan/manmu/memory/UserProfileRepository.java` | 创建 |
| 服务 | `src/main/java/top/lanshan/manmu/memory/UserProfileService.java` | 创建 |
| 配置 | `src/main/java/top/lanshan/manmu/config/UserProfileProperties.java` | 创建 |
| 配置 | `src/main/resources/application.yml` | 修改 |
| 注册 | `src/main/java/top/lanshan/manmu/DeepResearchMvpApplication.java` | 修改 |
| 接口 | `src/main/java/top/lanshan/manmu/agent/CoordinatorAgent.java` | 修改 |
| 实现 | `src/main/java/top/lanshan/manmu/agent/LlmCoordinatorAgent.java` | 修改 |
| 节点 | `src/main/java/top/lanshan/manmu/node/CoordinatorNode.java` | 修改 |
| 测试 | `src/test/java/top/lanshan/manmu/config/UserProfilePropertiesTest.java` | 创建 |

---

### Task 1: 创建 Flyway V5 迁移

**Files:**
- Create: `src/main/resources/db/migration/V5__create_user_profiles.sql`

- [ ] **Step 1: 写入迁移文件**

```sql
CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    profile_summary TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_session_id
    ON user_profiles (session_id);
```

- [ ] **Step 2: 启动应用验证 Flyway 执行 V5**

```bash
docker ps --filter name=manmu-postgres --format "{{.Names}} {{.Status}}"
# Expected: manmu-postgres Up ... (healthy)
```

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'
Start-Process -FilePath "mvn" -ArgumentList '-B', 'spring-boot:run', '-Dspring-boot.run.profiles=real-model', '-Dspring-boot.run.arguments=--server.port=18080' -RedirectStandardOutput "target/phase2-v5.log" -RedirectStandardError "target/phase2-v5-err.log" -NoNewWindow
```

Wait ~20 seconds, then:

```bash
grep -E "Successfully validated.*migrations|Migrating schema" target/phase2-v5.log | tail -3
# Expected: "Successfully validated 5 migrations"
```

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "\d user_profiles"
# Expected: 显示 id, session_id, profile_summary, updated_at 四列 + PK + index
```

- [ ] **Step 3: 停止服务**

```powershell
$port = 18080
$pid = (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue).OwningProcess
if ($pid) { Stop-Process -Id $pid -Force }
```

- [ ] **Step 4: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/resources/db/migration/V5__create_user_profiles.sql && git commit -m "添加 user_profiles 表 Flyway 迁移（V5）"
```

---

### Task 2: 创建领域 Record、实体和仓库

**Files:**
- Create: `src/main/java/top/lanshan/manmu/memory/UserProfileRecord.java`
- Create: `src/main/java/top/lanshan/manmu/memory/UserProfileEntity.java`
- Create: `src/main/java/top/lanshan/manmu/memory/UserProfileRepository.java`

- [ ] **Step 1: 创建 UserProfileRecord**

参照 `src/main/java/top/lanshan/manmu/memory/ConversationMessageRecord.java` 的 record 模式：

```java
package top.lanshan.manmu.memory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record UserProfileRecord(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("profile_summary") String profileSummary,
        @JsonProperty("updated_at") Instant updatedAt) {
}
```

- [ ] **Step 2: 创建 UserProfileEntity**

参照 `src/main/java/top/lanshan/manmu/memory/ConversationMessageEntity.java` 的 Persistable 模式：

```java
package top.lanshan.manmu.memory;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("user_profiles")
public class UserProfileEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean newEntity;

    @Column("session_id")
    private String sessionId;

    @Column("profile_summary")
    private String profileSummary;

    @Column("updated_at")
    private Instant updatedAt;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setNewEntity(boolean newEntity) {
        this.newEntity = newEntity;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getProfileSummary() {
        return profileSummary;
    }

    public void setProfileSummary(String profileSummary) {
        this.profileSummary = profileSummary;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    UserProfileRecord toRecord() {
        return new UserProfileRecord(sessionId, profileSummary, updatedAt);
    }
}
```

- [ ] **Step 3: 创建 UserProfileRepository**

参照 `src/main/java/top/lanshan/manmu/memory/ConversationMessageRepository.java`：

```java
package top.lanshan.manmu.memory;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserProfileRepository extends ReactiveCrudRepository<UserProfileEntity, UUID> {

    Mono<UserProfileEntity> findTopBySessionIdOrderByUpdatedAtDesc(String sessionId);
}
```

- [ ] **Step 4: 编译验证**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B compile 2>&1 | Select-Object -Last 5
# Expected: BUILD SUCCESS
```

- [ ] **Step 5: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/memory/UserProfileRecord.java src/main/java/top/lanshan/manmu/memory/UserProfileEntity.java src/main/java/top/lanshan/manmu/memory/UserProfileRepository.java && git commit -m "添加 UserProfileRecord、UserProfileEntity 和 UserProfileRepository"
```

---

### Task 3: 创建 UserProfileProperties 配置

**Files:**
- Create: `src/main/java/top/lanshan/manmu/config/UserProfileProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/top/lanshan/manmu/DeepResearchMvpApplication.java`

- [ ] **Step 1: 创建 UserProfileProperties**

参照 `src/main/java/top/lanshan/manmu/config/MemoryProperties.java`：

```java
package top.lanshan.manmu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mvp.memory.user-profile")
public class UserProfileProperties {

    private boolean enabled = true;

    private int maxMessagesForExtraction = 10;

    private int cacheMinutes = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxMessagesForExtraction() {
        return maxMessagesForExtraction;
    }

    public void setMaxMessagesForExtraction(int maxMessagesForExtraction) {
        this.maxMessagesForExtraction = maxMessagesForExtraction;
    }

    public int getCacheMinutes() {
        return cacheMinutes;
    }

    public void setCacheMinutes(int cacheMinutes) {
        this.cacheMinutes = cacheMinutes;
    }
}
```

- [ ] **Step 2: 在 application.yml 中添加配置**

读取 `src/main/resources/application.yml`，在 `mvp.memory.conversation:` 块之后追加：

```yaml
    user-profile:
      enabled: true
      max-messages-for-extraction: 10
      cache-minutes: 60
```

实际编辑：找到现有的 `max-message-characters: 800` 行，在其后插入 user-profile 配置块。

`old_string`:
```yaml
      max-message-characters: 800
```

`new_string`:
```yaml
      max-message-characters: 800
    user-profile:
      enabled: true
      max-messages-for-extraction: 10
      cache-minutes: 60
```

- [ ] **Step 3: 修改 DeepResearchMvpApplication 注册 UserProfileProperties**

读取 `src/main/java/top/lanshan/manmu/DeepResearchMvpApplication.java`，当前内容：

```java
import top.lanshan.manmu.config.MemoryProperties;
import top.lanshan.manmu.config.RagProperties;

@SpringBootApplication
@EnableConfigurationProperties({RagProperties.class, MemoryProperties.class})
```

修改为：

```java
import top.lanshan.manmu.config.MemoryProperties;
import top.lanshan.manmu.config.RagProperties;
import top.lanshan.manmu.config.UserProfileProperties;

@SpringBootApplication
@EnableConfigurationProperties({RagProperties.class, MemoryProperties.class, UserProfileProperties.class})
```

- [ ] **Step 4: 编译验证**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B compile 2>&1 | Select-Object -Last 5
# Expected: BUILD SUCCESS
```

- [ ] **Step 5: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/config/UserProfileProperties.java src/main/resources/application.yml src/main/java/top/lanshan/manmu/DeepResearchMvpApplication.java && git commit -m "添加 UserProfileProperties 配置并注册到应用上下文"
```

---

### Task 4: 实现 UserProfileService

**Files:**
- Create: `src/main/java/top/lanshan/manmu/memory/UserProfileService.java`

**注意：** 本服务运行在 `Flux.defer()` 内，使用同步 `.block()` 调用 R2DBC 仓库（符合项目现有的 `LlmCoordinatorAgent` 同步模式）。

- [ ] **Step 1: 创建 UserProfileService**

```java
package top.lanshan.manmu.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.config.UserProfileProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserProfileService {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository profileRepository;
    private final ConversationMessageRepository messageRepository;
    private final AgentClient agentClient;
    private final UserProfileProperties properties;

    public UserProfileService(UserProfileRepository profileRepository,
            ConversationMessageRepository messageRepository,
            AgentClient agentClient,
            UserProfileProperties properties) {
        this.profileRepository = profileRepository;
        this.messageRepository = messageRepository;
        this.agentClient = agentClient;
        this.properties = properties;
    }

    public String getOrCreateProfile(String sessionId) {
        if (!properties.isEnabled()) {
            return "";
        }
        if (sessionId == null || sessionId.isBlank()) {
            return "";
        }

        UserProfileEntity existing = profileRepository
                .findTopBySessionIdOrderByUpdatedAtDesc(sessionId)
                .block();
        if (existing != null && isRecent(existing.getUpdatedAt())) {
            return existing.getProfileSummary();
        }

        List<String> recentMessages = messageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId)
                .takeLast(properties.getMaxMessagesForExtraction())
                .map(ConversationMessageEntity::getContent)
                .collectList()
                .block();
        if (recentMessages == null || recentMessages.isEmpty()) {
            return "";
        }

        String conversationText = recentMessages.stream()
                .collect(Collectors.joining("\n"));
        String profileSummary = extractProfile(conversationText);
        if (profileSummary == null || profileSummary.isBlank()) {
            return "";
        }

        saveProfile(sessionId, profileSummary);
        return profileSummary;
    }

    private boolean isRecent(Instant updatedAt) {
        if (updatedAt == null) {
            return false;
        }
        long cacheSeconds = (long) properties.getCacheMinutes() * 60;
        return Instant.now().minusSeconds(cacheSeconds).isBefore(updatedAt);
    }

    private String extractProfile(String conversationText) {
        String systemPrompt = """
                You are a user profile extractor. Based on the user's conversation messages, \
                summarize the user's role, background, and expertise level in ONE concise sentence. \
                Focus on information that would help tailor research responses to this user. \
                If you cannot determine any profile information, output "general user". \
                Output ONLY the summary sentence, nothing else.\
                """;

        String userPrompt = "User conversation messages:\n" + conversationText;

        try {
            String result = agentClient.call(systemPrompt, userPrompt);
            if (result == null || result.isBlank()) {
                return "";
            }
            return result.trim();
        } catch (Exception e) {
            logger.warn("Failed to extract user profile: {}", e.getMessage());
            return "";
        }
    }

    private void saveProfile(String sessionId, String profileSummary) {
        UserProfileEntity entity = new UserProfileEntity();
        entity.setNewEntity(true);
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setProfileSummary(profileSummary);
        entity.setUpdatedAt(Instant.now());
        profileRepository.save(entity).block();
    }
}
```

- [ ] **Step 2: 编译验证**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B compile 2>&1 | Select-Object -Last 5
# Expected: BUILD SUCCESS
```

- [ ] **Step 3: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/memory/UserProfileService.java && git commit -m "添加 UserProfileService 用户角色提取服务"
```

---

### Task 5: 集成到 CoordinatorNode——修改接口、实现和节点

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/agent/CoordinatorAgent.java`
- Modify: `src/main/java/top/lanshan/manmu/agent/LlmCoordinatorAgent.java`
- Modify: `src/main/java/top/lanshan/manmu/node/CoordinatorNode.java`

- [ ] **Step 1: 修改 CoordinatorAgent 接口**

读取 `src/main/java/top/lanshan/manmu/agent/CoordinatorAgent.java`，当前内容：

```java
package top.lanshan.manmu.agent;

import top.lanshan.manmu.model.CoordinatorDecision;

public interface CoordinatorAgent {

    CoordinatorDecision coordinate(String query, boolean deepResearchEnabled);

}
```

修改 `coordinate` 方法签名，增加 `userProfileContext` 参数：

将：
```java
    CoordinatorDecision coordinate(String query, boolean deepResearchEnabled);
```

替换为：
```java
    CoordinatorDecision coordinate(String query, boolean deepResearchEnabled, String userProfileContext);
```

- [ ] **Step 2: 修改 LlmCoordinatorAgent**

读取 `src/main/java/top/lanshan/manmu/agent/LlmCoordinatorAgent.java`。需要三处修改：

**修改 1：** 更新 `coordinate` 方法签名以匹配接口。

将：
```java
    public CoordinatorDecision coordinate(String query, boolean deepResearchEnabled) {
```

替换为：
```java
    public CoordinatorDecision coordinate(String query, boolean deepResearchEnabled, String userProfileContext) {
```

**修改 2：** 在 user prompt 中注入 profile context。

当前 user prompt 构建代码：
```java
        String userPrompt = """
                User question:
                %s

                Deep research is enabled: %s
                """.formatted(query, deepResearchEnabled);
```

将整个 user prompt 构建块替换为：
```java
        String profileSection = (userProfileContext != null && !userProfileContext.isBlank())
                ? "\nUser background: " + userProfileContext + "\n"
                : "";

        String userPrompt = """
                User question:
                %s

                Deep research is enabled: %s%s
                """.formatted(query, deepResearchEnabled, profileSection);
```

- [ ] **Step 3: 修改 CoordinatorNode**

读取 `src/main/java/top/lanshan/manmu/node/CoordinatorNode.java`。需要在 `run()` 方法中注入 `UserProfileService` 并在调用 `coordinatorAgent.coordinate()` 前提取 profile。

**完整替换文件内容：**

将整个 `CoordinatorNode.java` 从当前的：

```java
package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.agent.CoordinatorAgent;
import top.lanshan.manmu.model.CoordinatorDecision;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;

@Component
public class CoordinatorNode implements ResearchNode {

    private final CoordinatorAgent coordinatorAgent;

    public CoordinatorNode(CoordinatorAgent coordinatorAgent) {
        this.coordinatorAgent = coordinatorAgent;
    }

    @Override
    public int order() {
        return 1;
    }

    @Override
    public String name() {
        return "coordinator";
    }

    @Override
    public Flux<ResearchEvent> run(ResearchState state) {
        return Flux.defer(() -> {
            CoordinatorDecision decision = coordinatorAgent.coordinate(state.query(), state.deepResearchEnabled());
            state.coordinatorDecision(decision);
            if (decision.directAnswerRoute()) {
                state.report(decision.directAnswer());
            }
            return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision",
                    "Coordinator routed to " + decision.nextRoute().name().toLowerCase(), decision));
        });
    }

}
```

改为：

```java
package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.agent.CoordinatorAgent;
import top.lanshan.manmu.memory.UserProfileService;
import top.lanshan.manmu.model.CoordinatorDecision;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;

@Component
public class CoordinatorNode implements ResearchNode {

    private final CoordinatorAgent coordinatorAgent;

    private final UserProfileService userProfileService;

    public CoordinatorNode(CoordinatorAgent coordinatorAgent, UserProfileService userProfileService) {
        this.coordinatorAgent = coordinatorAgent;
        this.userProfileService = userProfileService;
    }

    @Override
    public int order() {
        return 1;
    }

    @Override
    public String name() {
        return "coordinator";
    }

    @Override
    public Flux<ResearchEvent> run(ResearchState state) {
        return Flux.defer(() -> {
            String userProfileContext = userProfileService.getOrCreateProfile(state.sessionId());
            CoordinatorDecision decision = coordinatorAgent.coordinate(
                    state.query(), state.deepResearchEnabled(), userProfileContext);
            state.coordinatorDecision(decision);
            if (decision.directAnswerRoute()) {
                state.report(decision.directAnswer());
            }
            return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision",
                    "Coordinator routed to " + decision.nextRoute().name().toLowerCase(), decision));
        });
    }

}
```

- [ ] **Step 4: 编译验证**

注意：修改 `CoordinatorAgent` 接口后，IDE 可能报告其他实现了该接口的类编译错误。需要检查项目中是否有其他 `CoordinatorAgent` 实现（如 mock 实现），如有则同步更新。

先检查是否还有其他实现：

```bash
grep -r "implements CoordinatorAgent" src/ --include="*.java"
grep -r "implements CoordinatorAgent" src/test/ --include="*.java"
```

如果有测试中的 mock 实现，同步更新其 `coordinate` 方法签名。

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B compile 2>&1 | Select-Object -Last 15
# Expected: BUILD SUCCESS
```

如果编译失败，根据错误信息修复后重新编译。

- [ ] **Step 5: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/main/java/top/lanshan/manmu/agent/CoordinatorAgent.java src/main/java/top/lanshan/manmu/agent/LlmCoordinatorAgent.java src/main/java/top/lanshan/manmu/node/CoordinatorNode.java && git commit -m "集成 UserProfileService 到 CoordinatorNode，注入用户角色上下文到 prompt"
```

---

### Task 6: 单元测试

**Files:**
- Create: `src/test/java/top/lanshan/manmu/config/UserProfilePropertiesTest.java`

- [ ] **Step 1: 创建 UserProfilePropertiesTest**

参照 `src/test/java/top/lanshan/manmu/config/MemoryPropertiesTest.java`：

```java
package top.lanshan.manmu.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfilePropertiesTest {

    @Test
    void defaultsEnableUserProfile() {
        UserProfileProperties properties = new UserProfileProperties();
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    void defaultMaxMessagesForExtractionIs10() {
        UserProfileProperties properties = new UserProfileProperties();
        assertThat(properties.getMaxMessagesForExtraction()).isEqualTo(10);
    }

    @Test
    void defaultCacheMinutesIs60() {
        UserProfileProperties properties = new UserProfileProperties();
        assertThat(properties.getCacheMinutes()).isEqualTo(60);
    }

    @Test
    void settersOverrideDefaults() {
        UserProfileProperties properties = new UserProfileProperties();
        properties.setEnabled(false);
        properties.setMaxMessagesForExtraction(5);
        properties.setCacheMinutes(30);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getMaxMessagesForExtraction()).isEqualTo(5);
        assertThat(properties.getCacheMinutes()).isEqualTo(30);
    }
}
```

- [ ] **Step 2: 运行 UserProfilePropertiesTest**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B '-Dtest=UserProfilePropertiesTest' test 2>&1 | Select-Object -Last 8
# Expected: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS
```

- [ ] **Step 3: 运行全量测试确保无回归**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B test 2>&1 | Select-String -Pattern "Tests run:.*Failures|BUILD" | Select-Object -Last 5
# Expected: Tests run: 141, Failures: 0, Errors: 12（12 errors 为 PostgreSQL 预存问题）
```

- [ ] **Step 4: 提交**

```bash
cd C:/MainData/code/Claude_project/M-agent && git add src/test/java/top/lanshan/manmu/config/UserProfilePropertiesTest.java && git commit -m "添加 UserProfilePropertiesTest 单元测试"
```

---

## Task 7: 全链路验证（真实 API 调用）

**Files:**
- Create: `target/http-check/phase2/request.json`（验证用临时文件）

- [ ] **Step 1: 启动后端服务**

确认 PostgreSQL 正在运行：

```bash
docker ps --filter name=manmu-postgres --format "{{.Names}} {{.Status}}"
```

初始化对话消息（写入一条用户自述消息到 conversation_messages 表，模拟 Phase 1 已有对话历史）：

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "
INSERT INTO conversation_messages (id, session_id, thread_id, role, content, created_at)
VALUES (gen_random_uuid(), 'test-profile-session', 'test-profile-thread', 'USER',
'我是 Python 初学者，之前只用过 Excel 做数据分析，现在想用 Python 做。请用简单易懂的方式解释。',
NOW());
"
```

启动后端：

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'
Start-Process -FilePath "mvn" -ArgumentList '-B', 'spring-boot:run', '-Dspring-boot.run.profiles=real-model', '-Dspring-boot.run.arguments=--server.port=18080' -RedirectStandardOutput "target/phase2-run.log" -RedirectStandardError "target/phase2-run-err.log" -NoNewWindow
```

等待 ~20 秒确认启动：

```bash
grep "Started DeepResearchMvpApplication" target/phase2-run.log
```

- [ ] **Step 2: 执行研究请求**

```bash
mkdir -p target/http-check/phase2
```

写入请求体：

```bash
echo '{"query":"Python 数据分析入门需要学什么？","sessionId":"test-profile-session","enableDeepResearch":true}' > target/http-check/phase2/request.json
```

发送请求（SSE 流式输出）：

```bash
curl.exe -s -N -X POST http://localhost:18080/api/research/stream \
  -H "Content-Type: application/json" \
  --data-binary "@target/http-check/phase2/request.json" \
  > target/http-check/phase2/response.sse 2>&1 &
```

等待 ~2 分钟，检查 SSE 输出：

```bash
grep -E "done|error" target/http-check/phase2/response.sse | head -5
# Expected: 最终出现 "done" 事件
```

- [ ] **Step 3: 验证 user_profiles 表有提取结果**

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "SELECT * FROM user_profiles WHERE session_id = 'test-profile-session';"
# Expected: 返回一行，profile_summary 包含对用户背景的描述（如 "beginner" 或 "Python"）
```

- [ ] **Step 4: 验证 Coordinator prompt 包含用户背景**

```bash
grep -i "background\|profile\|beginner\|python\|User background" target/phase2-run.log | head -5
# Expected: 日志中应该能看到 Coordinator 的 system prompt 或相关输出
```

- [ ] **Step 5: 停止服务并清理测试数据**

```powershell
$port = 18080
$pid = (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue).OwningProcess
if ($pid) { Stop-Process -Id $pid -Force }
```

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "DELETE FROM conversation_messages WHERE session_id = 'test-profile-session';"
docker exec manmu-postgres psql -U manmu -d manmu -c "DELETE FROM user_profiles WHERE session_id = 'test-profile-session';"
```

- [ ] **Step 6: （可选）提交全链路验证脚本和结果**

如果验证通过，可将结果文件提交或保留到 target/（已在 .gitignore 中）。

---

## Phase 2 完成标准

1. ✅ `user_profiles` 表存在且有正确的列和索引
2. ✅ `mvn compile` 通过
3. ✅ `mvn test` 通过（141 tests, 0 failures, 12 expected errors）
4. ✅ 全链路验证：user_profiles 表中成功写入 LLM 提取的用户角色摘要
5. ✅ Coordinator prompt 中注入用户背景，研究结果更贴合用户水平
6. ✅ 所有 7 个新文件已创建，3 个文件已修改，全部提交
