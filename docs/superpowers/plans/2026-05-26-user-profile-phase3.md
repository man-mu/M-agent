# 用户画像增强 — Phase 3 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将用户画像从一句自由文本升级为结构化多字段描述，并注入 `ReporterNode` 使最终报告受益于用户画像。

**Architecture:** 在 `user_profiles` 表新增 3 列（`expertise_level`、`detail_preference`、`style_preference`），修改 `UserProfileService.extractProfile()` 的 LLM prompt 使其输出结构化 JSON，历史画像作为上下文实现增量更新。`ReporterNode` 注入 `UserProfileService`，将画像上下文传入 `ReporterAgent` 的 user prompt。

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring Data R2DBC, PostgreSQL 17, Flyway

---

## 关键设计决策

| 维度 | 决定 |
|------|------|
| 画像字段 | 原有 `profile_summary` + 新增 3 列：`expertise_level`、`detail_preference`、`style_preference` |
| LLM 输出 | JSON 格式，含 4 个字段，解析后分别存储 |
| 增量更新 | 一次 LLM 调用，将前次画像 JSON 作为上下文传入 prompt |
| 注入范围 | `CoordinatorNode`（已有）+ `ReporterNode`（新增） |
| Reporter 注入方式 | 拼接画像字段到 Reporter 的 user prompt |
| 配置开关 | `mvp.memory.user-profile.guide-reporter`（默认 true） |
| prompt 方式 | 内联（不单独建 prompt 文件） |

## 文件结构总览

| 层 | 文件 | 操作 |
|---|------|------|
| 迁移 | `src/main/resources/db/migration/V6__alter_user_profiles.sql` | 创建 |
| Record | `src/main/java/top/lanshan/manmu/memory/UserProfileRecord.java` | 修改 |
| 实体 | `src/main/java/top/lanshan/manmu/memory/UserProfileEntity.java` | 修改 |
| 服务 | `src/main/java/top/lanshan/manmu/memory/UserProfileService.java` | 修改 |
| 配置 | `src/main/java/top/lanshan/manmu/config/UserProfileProperties.java` | 修改 |
| 配置 | `src/main/resources/application.yml` | 修改 |
| 接口 | `src/main/java/top/lanshan/manmu/agent/ReporterAgent.java` | 修改 |
| 实现 | `src/main/java/top/lanshan/manmu/agent/LlmReporterAgent.java` | 修改 |
| 节点 | `src/main/java/top/lanshan/manmu/node/ReporterNode.java` | 修改 |
| 测试 | `src/test/java/top/lanshan/manmu/config/UserProfilePropertiesTest.java` | 修改 |
| 测试 | `src/test/java/top/lanshan/manmu/node/ReporterNodeTest.java` | 创建 |

---

### Task 1: 创建 Flyway V6 迁移——新增 3 列

**Files:**
- Create: `src/main/resources/db/migration/V6__alter_user_profiles.sql`

- [ ] **Step 1: 写入 V6 迁移文件**

```sql
ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS expertise_level VARCHAR(32),
    ADD COLUMN IF NOT EXISTS detail_preference VARCHAR(32),
    ADD COLUMN IF NOT EXISTS style_preference VARCHAR(32);
```

- [ ] **Step 2: 启动应用验证 Flyway 执行 V6**

```bash
docker ps --filter name=manmu-postgres --format "{{.Names}} {{.Status}}"
# Expected: manmu-postgres Up ... (healthy)
```

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'
Start-Process -FilePath "mvn" -ArgumentList '-B', 'spring-boot:run', '-Dspring-boot.run.profiles=real-model', '-Dspring-boot.run.arguments=--server.port=18080' -RedirectStandardOutput "target/phase3-v6.log" -RedirectStandardError "target/phase3-v6-err.log" -NoNewWindow
```

等待 ~20 秒后：

```bash
grep -E "Successfully validated.*migrations|Successfully applied" target/phase3-v6.log | tail -3
# Expected: "Successfully validated 6 migrations" 或 "Successfully applied 1 migration to schema "public", now at version v6"
```

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "\d user_profiles"
# Expected: 显示 7 列（原有 4 列 + expertise_level, detail_preference, style_preference）
```

- [ ] **Step 3: 停止服务**

```powershell
$javaHome = 'C:\WorkResources\JDKs\JDK17'
$port = 18080
$owner = (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue).OwningProcess
if ($owner) { Stop-Process -Id $owner -Force }
```

- [ ] **Step 4: 提交**

```bash
git add src/main/resources/db/migration/V6__alter_user_profiles.sql && git commit -m "V6 迁移：user_profiles 表新增用户画像结构化字段"
```

---

### Task 2: 更新 UserProfileRecord 和 UserProfileEntity

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/memory/UserProfileRecord.java`
- Modify: `src/main/java/top/lanshan/manmu/memory/UserProfileEntity.java`

- [ ] **Step 1: 修改 UserProfileRecord**

当前文件内容：

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

替换为：

```java
package top.lanshan.manmu.memory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record UserProfileRecord(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("profile_summary") String profileSummary,
        @JsonProperty("expertise_level") String expertiseLevel,
        @JsonProperty("detail_preference") String detailPreference,
        @JsonProperty("style_preference") String stylePreference,
        @JsonProperty("updated_at") Instant updatedAt) {
}
```

- [ ] **Step 2: 修改 UserProfileEntity**

当前文件 `src/main/java/top/lanshan/manmu/memory/UserProfileEntity.java` 需要：

**修改 1：** 在 `profileSummary` 字段后新增 3 个字段：

```java
    @Column("expertise_level")
    private String expertiseLevel;

    @Column("detail_preference")
    private String detailPreference;

    @Column("style_preference")
    private String stylePreference;
```

**修改 2：** 新增 6 个 getter/setter（每个字段一对）：

```java
    public String getExpertiseLevel() {
        return expertiseLevel;
    }

    public void setExpertiseLevel(String expertiseLevel) {
        this.expertiseLevel = expertiseLevel;
    }

    public String getDetailPreference() {
        return detailPreference;
    }

    public void setDetailPreference(String detailPreference) {
        this.detailPreference = detailPreference;
    }

    public String getStylePreference() {
        return stylePreference;
    }

    public void setStylePreference(String stylePreference) {
        this.stylePreference = stylePreference;
    }
```

**修改 3：** 更新 `toRecord()` 方法：

```java
    UserProfileRecord toRecord() {
        return new UserProfileRecord(sessionId, profileSummary, expertiseLevel, detailPreference, stylePreference, updatedAt);
    }
```

**修改 4：** 将 `setNewEntity` 方法的内容从 `this.newEntity = newEntity;` 保持不变即可。

完整替换后的文件内容为：

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

    @Column("expertise_level")
    private String expertiseLevel;

    @Column("detail_preference")
    private String detailPreference;

    @Column("style_preference")
    private String stylePreference;

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

    public String getExpertiseLevel() {
        return expertiseLevel;
    }

    public void setExpertiseLevel(String expertiseLevel) {
        this.expertiseLevel = expertiseLevel;
    }

    public String getDetailPreference() {
        return detailPreference;
    }

    public void setDetailPreference(String detailPreference) {
        this.detailPreference = detailPreference;
    }

    public String getStylePreference() {
        return stylePreference;
    }

    public void setStylePreference(String stylePreference) {
        this.stylePreference = stylePreference;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    UserProfileRecord toRecord() {
        return new UserProfileRecord(sessionId, profileSummary, expertiseLevel, detailPreference, stylePreference, updatedAt);
    }
}
```

- [ ] **Step 3: 编译验证**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B compile 2>&1 | Select-Object -Last 5
# Expected: BUILD SUCCESS
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/top/lanshan/manmu/memory/UserProfileRecord.java src/main/java/top/lanshan/manmu/memory/UserProfileEntity.java && git commit -m "UserProfile 实体和 Record 新增 expertise_level/detail_preference/style_preference 字段"
```

---

### Task 3: 更新 UserProfileProperties——添加 guideReporter

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/config/UserProfileProperties.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 修改 UserProfileProperties**

在 `cacheMinutes` 字段后新增 `guideReporter` 字段：

```java
    private boolean guideReporter = true;

    public boolean isGuideReporter() {
        return guideReporter;
    }

    public void setGuideReporter(boolean guideReporter) {
        this.guideReporter = guideReporter;
    }
```

编辑操作：在当前文件的 `private int cacheMinutes = 60;` 行之后插入上述代码块。

`old_string`:
```java
    private int cacheMinutes = 60;
```

`new_string`:
```java
    private int cacheMinutes = 60;

    private boolean guideReporter = true;
```

然后在 `setCacheMinutes` 方法之后插入 getter/setter：

`old_string`:
```java
    public void setCacheMinutes(int cacheMinutes) {
        this.cacheMinutes = cacheMinutes;
    }
}
```

`new_string`:
```java
    public void setCacheMinutes(int cacheMinutes) {
        this.cacheMinutes = cacheMinutes;
    }

    public boolean isGuideReporter() {
        return guideReporter;
    }

    public void setGuideReporter(boolean guideReporter) {
        this.guideReporter = guideReporter;
    }
}
```

- [ ] **Step 2: 在 application.yml 中添加配置**

在 `cache-minutes: 60` 行后追加：

`old_string`:
```yaml
      cache-minutes: 60
```

`new_string`:
```yaml
      cache-minutes: 60
      guide-reporter: true
```

- [ ] **Step 3: 编译验证**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B compile 2>&1 | Select-Object -Last 5
# Expected: BUILD SUCCESS
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/top/lanshan/manmu/config/UserProfileProperties.java src/main/resources/application.yml && git commit -m "UserProfileProperties 新增 guide-reporter 配置项"
```

---

### Task 4: 增强 UserProfileService——结构化提取 + 增量更新

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/memory/UserProfileService.java`

**注意：** 本服务运行在 `Flux.defer()` 内，使用同步 `.block()` 调用 R2DBC 仓库。本次改动保持此模式不变。

- [ ] **Step 1: 重写 UserProfileService**

需要三处改动：

**改动 1：** `extractProfile()` 方法 —— LLM prompt 改为结构化 JSON 输出，带历史画像上下文：

```java
    private String extractProfile(String conversationText, UserProfileEntity previous) {
        String historyContext = "";
        if (previous != null && previous.getProfileSummary() != null) {
            historyContext = """
                    
                    Previous profile:
                    - Role: %s
                    - Expertise: %s
                    - Detail: %s
                    - Style: %s
                    
                    Update this profile if the user's new messages reveal more or different information.\
                    """.formatted(
                    previous.getProfileSummary() != null ? previous.getProfileSummary() : "unknown",
                    previous.getExpertiseLevel() != null ? previous.getExpertiseLevel() : "unknown",
                    previous.getDetailPreference() != null ? previous.getDetailPreference() : "unknown",
                    previous.getStylePreference() != null ? previous.getStylePreference() : "unknown");
        }

        String systemPrompt = """
                You are a user profile extractor. Based on the user's conversation messages, \
                extract structured profile information. %s \
                Output ONLY valid JSON with these four fields:
                {
                  "profile_summary": "one sentence describing the user's role and background",
                  "expertise_level": "beginner|intermediate|advanced",
                  "detail_preference": "concise|balanced|comprehensive",
                  "style_preference": "practical|theoretical|mixed"
                }
                Output ONLY the JSON object, nothing else.\
                """.formatted(previous != null ? "The previous profile is provided — update it if new information is richer or different." : "");

        String userPrompt = "User conversation messages:" + historyContext + "\n" + conversationText;

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
```

注意原 `extractProfile` 方法签名是 `extractProfile(String conversationText)`，需要改为 `extractProfile(String conversationText, UserProfileEntity previous)`。

**改动 2：** 新增 `parseAndFillEntity` 方法解析 LLM 返回的 JSON：

```java
    private void parseAndFillEntity(UserProfileEntity entity, String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
            if (node.has("profile_summary")) {
                entity.setProfileSummary(node.get("profile_summary").asText());
            }
            if (node.has("expertise_level")) {
                entity.setExpertiseLevel(node.get("expertise_level").asText());
            }
            if (node.has("detail_preference")) {
                entity.setDetailPreference(node.get("detail_preference").asText());
            }
            if (node.has("style_preference")) {
                entity.setStylePreference(node.get("style_preference").asText());
            }
        } catch (Exception e) {
            logger.warn("Failed to parse profile JSON, using raw text as summary: {}", e.getMessage());
            entity.setProfileSummary(json);
        }
    }
```

**改动 3：** `getOrCreateProfile` 方法 —— 将历史画像传给 extractProfile，解析 JSON 后存储：

将 `getOrCreateProfile` 方法体中这两行：

```java
        if (existing != null && isRecent(existing.getUpdatedAt())) {
            return existing.getProfileSummary();
        }
```

替换为（返回格式化后的完整上下文，不只是 profileSummary）：

```java
        if (existing != null && isRecent(existing.getUpdatedAt())) {
            return formatProfileContext(existing);
        }
```

将以下提取+存储逻辑：

```java
        String conversationText = recentMessages.stream()
                .collect(Collectors.joining("\n"));
        String profileSummary = extractProfile(conversationText);
        if (profileSummary == null || profileSummary.isBlank()) {
            return "";
        }

        saveProfile(sessionId, profileSummary);
        return profileSummary;
```

替换为：

```java
        String conversationText = recentMessages.stream()
                .collect(Collectors.joining("\n"));
        String rawResult = extractProfile(conversationText, existing);
        if (rawResult == null || rawResult.isBlank()) {
            return "";
        }

        UserProfileEntity entity = new UserProfileEntity();
        entity.setNewEntity(true);
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setUpdatedAt(Instant.now());
        parseAndFillEntity(entity, rawResult);
        profileRepository.save(entity).block();
        return formatProfileContext(entity);
```

**改动 4：** 新增 `formatProfileContext` 方法 —— 将实体字段格式化为注入 prompt 的文本：

```java
    private String formatProfileContext(UserProfileEntity entity) {
        StringBuilder sb = new StringBuilder();
        if (entity.getProfileSummary() != null && !entity.getProfileSummary().isBlank()) {
            sb.append(entity.getProfileSummary());
        }
        if (entity.getExpertiseLevel() != null && !entity.getExpertiseLevel().isBlank()) {
            sb.append(" (expertise: ").append(entity.getExpertiseLevel()).append(")");
        }
        if (entity.getDetailPreference() != null && !entity.getDetailPreference().isBlank()) {
            sb.append(", detail: ").append(entity.getDetailPreference());
        }
        if (entity.getStylePreference() != null && !entity.getStylePreference().isBlank()) {
            sb.append(", style: ").append(entity.getStylePreference());
        }
        return sb.toString();
    }
```

**改动 5：** 删除旧的 `saveProfile` 方法（`parseAndFillEntity` + 内联 save 已取代它）。

**改动 6：** 在文件顶部新增 `ObjectMapper` 静态字段（或使用局部变量，见改动 2 已在方法内创建）。建议在类级别新增：

```java
    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
```

然后将 `parseAndFillEntity` 方法中的局部变量 `mapper` 改为使用 `objectMapper`。

- [ ] **Step 2: 编译验证**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B compile 2>&1 | Select-Object -Last 15
# Expected: BUILD SUCCESS
```

如果编译失败，根据错误信息修复后重新编译。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/top/lanshan/manmu/memory/UserProfileService.java && git commit -m "增强 UserProfileService：结构化 JSON 提取 + 历史画像增量更新"
```

---

### Task 5: 修改 ReporterAgent 接口和 LlmReporterAgent

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/agent/ReporterAgent.java`
- Modify: `src/main/java/top/lanshan/manmu/agent/LlmReporterAgent.java`

- [ ] **Step 1: 修改 ReporterAgent 接口**

将 `report` 方法签名从：

```java
    String report(ResearchState state);
```

替换为：

```java
    String report(ResearchState state, String userProfileContext);
```

- [ ] **Step 2: 修改 LlmReporterAgent**

**修改 1：** 更新 `report` 方法签名：

将：
```java
    public String report(ResearchState state) {
```

替换为：
```java
    public String report(ResearchState state, String userProfileContext) {
```

**修改 2：** 在 user prompt 中注入画像上下文。当前的 user prompt 构建代码末尾是：

```java
        String userPrompt = """
                Query:
                %s

                Plan title:
                %s

                Plan thought:
                %s

                Plan steps:
                %s

                Research observations:
                %s

                Web search sources:
                %s

                Write the final answer as concise Markdown. Include:
                1. a short conclusion,
                2. key findings grounded in the observations,
                3. next implementation steps.
                """.formatted(state.query(), state.plan().title(), state.plan().thought(), steps, observations,
                searchSources.isBlank() ? "No web search sources were collected." : searchSources);
        return agentClient.call(promptService.load("reporter"), userPrompt);
```

替换为：

```java
        String profileSection = (userProfileContext != null && !userProfileContext.isBlank())
                ? "Report audience: " + userProfileContext + "\n"
                : "";

        String userPrompt = """
                Query:
                %s

                Plan title:
                %s

                Plan thought:
                %s

                Plan steps:
                %s

                Research observations:
                %s

                Web search sources:
                %s
                %s
                Write the final answer as concise Markdown. Include:
                1. a short conclusion,
                2. key findings grounded in the observations,
                3. next implementation steps.
                """.formatted(state.query(), state.plan().title(), state.plan().thought(), steps, observations,
                searchSources.isBlank() ? "No web search sources were collected." : searchSources, profileSection);
        return agentClient.call(promptService.load("reporter"), userPrompt);
```

- [ ] **Step 3: 检查是否有其他 ReporterAgent 实现**

```bash
grep -r "implements ReporterAgent" src/ --include="*.java"
grep -r "implements ReporterAgent" src/test/ --include="*.java"
```

如果有测试中的 mock 实现，同步更新其 `report` 方法签名。

- [ ] **Step 4: 编译验证**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B compile 2>&1 | Select-Object -Last 15
# Expected: BUILD SUCCESS
```

如果编译失败，根据错误信息修复后重新编译。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/top/lanshan/manmu/agent/ReporterAgent.java src/main/java/top/lanshan/manmu/agent/LlmReporterAgent.java && git commit -m "ReporterAgent 接口新增 userProfileContext 参数，LlmReporterAgent 注入画像到报告 prompt"
```

---

### Task 6: 修改 ReporterNode——注入 UserProfileService

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/node/ReporterNode.java`

- [ ] **Step 1: 重写 ReporterNode**

当前文件内容：

```java
package top.lanshan.manmu.node;

import top.lanshan.manmu.agent.ReporterAgent;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ReporterNode implements ResearchNode {

	private final ReporterAgent reporterAgent;

	public ReporterNode(ReporterAgent reporterAgent) {
		this.reporterAgent = reporterAgent;
	}

	@Override
	public int order() {
		return 50;
	}

	@Override
	public String name() {
		return "reporter";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			String report = reporterAgent.report(state);
			state.report(report);
			return Flux.just(
					ResearchEvent.message(state.threadId(), name(), "started", "Generating final report", null),
					ResearchEvent.message(state.threadId(), name(), "completed", report, report));
		});
	}

}
```

替换为：

```java
package top.lanshan.manmu.node;

import top.lanshan.manmu.agent.ReporterAgent;
import top.lanshan.manmu.config.UserProfileProperties;
import top.lanshan.manmu.memory.UserProfileService;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ReporterNode implements ResearchNode {

	private final ReporterAgent reporterAgent;

	private final UserProfileService userProfileService;

	private final UserProfileProperties userProfileProperties;

	public ReporterNode(ReporterAgent reporterAgent, UserProfileService userProfileService,
			UserProfileProperties userProfileProperties) {
		this.reporterAgent = reporterAgent;
		this.userProfileService = userProfileService;
		this.userProfileProperties = userProfileProperties;
	}

	@Override
	public int order() {
		return 50;
	}

	@Override
	public String name() {
		return "reporter";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			String userProfileContext = "";
			if (userProfileProperties != null && userProfileProperties.isGuideReporter()
					&& userProfileService != null) {
				userProfileContext = userProfileService.getOrCreateProfile(state.sessionId());
			}
			String report = reporterAgent.report(state, userProfileContext);
			state.report(report);
			return Flux.just(
					ResearchEvent.message(state.threadId(), name(), "started", "Generating final report", null),
					ResearchEvent.message(state.threadId(), name(), "completed", report, report));
		});
	}

}
```

**注意：** `ReporterNode` 的构造参数变化后，`GraphResearchRunnerTest` 中有多处 `new ReporterNode()` 调用会编译失败。但这些调用在 `test` 源码中，`mvn compile`（只编译 `src/main`）不会触发。修复放在 Task 7 中处理。

- [ ] **Step 2: 编译验证**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B compile 2>&1 | Select-Object -Last 15
# Expected: BUILD SUCCESS
```

如果编译失败，根据错误信息修复后重新编译。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/top/lanshan/manmu/node/ReporterNode.java && git commit -m "ReporterNode 注入 UserProfileService，报告生成时使用用户画像"
```

---

### Task 7: 更新测试

**Files:**
- Modify: `src/test/java/top/lanshan/manmu/config/UserProfilePropertiesTest.java`
- Create: `src/test/java/top/lanshan/manmu/node/ReporterNodeTest.java`（如需要）
- Check: 搜索因 Task 5/6 变更可能需要更新的测试文件

- [ ] **Step 1: 修复 GraphResearchRunnerTest——适配 ReporterNode 新构造参数**

`GraphResearchRunnerTest` 中有多处 `new ReporterNode()` 调用，构造参数变更后需要全部更新。

该测试文件使用 `ResearchGraphBuilder` 构建测试图，手动传入节点列表，同时也用 `@Bean` 工厂方法注册 `reporterNode`。

**修复方案：** ReporterNode 对 null `userProfileService`/`userProfileProperties` 做防御性处理（已在 Task 6 的 `run()` 实现中用 `if (userProfileProperties.isGuideReporter())` + 字段访问，实际上需要先判空）。

更新 `ReporterNode.run()` 中的判空逻辑，从：

```java
            String userProfileContext = "";
            if (userProfileProperties.isGuideReporter()) {
                userProfileContext = userProfileService.getOrCreateProfile(state.sessionId());
            }
```

改为：

```java
            String userProfileContext = "";
            if (userProfileProperties != null && userProfileProperties.isGuideReporter()
                    && userProfileService != null) {
                userProfileContext = userProfileService.getOrCreateProfile(state.sessionId());
            }
```

然后修改 `ReporterNode.java` 并提交此修复。

接着修改 `GraphResearchRunnerTest.java`：

**修改 1：** 测试类中新增一个静态 ReporterAgent stub：

```java
    private static final ReporterAgent NOOP_REPORTER = (state, userProfileContext) -> "Report for " + state.query();
```

**修改 2：** 全局替换所有 `new ReporterNode()` 为 `new ReporterNode(NOOP_REPORTER, null, null)`。

PowerShell 批量替换（14 处）：

```powershell
(Get-Content src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java -Raw) `
  -replace 'new ReporterNode\(\)', 'new ReporterNode(NOOP_REPORTER, null, null)' |
  Set-Content src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java
```

**修改 3：** 在 `reporterNode()` Bean 工厂方法中也要同步修改。第 469 行的 `new ReporterNode()` 需要改为使用 Bean 方法引用：

```java
    @Bean
    ResearchNode reporterNode() {
        return new ReporterNode(reporterAgent(), null, null);
    }
```

注意这里的 `reporterAgent()` 是同一个配置类中定义的 Bean（第 474 行），返回 `(query, step, searchContext) -> "Researched " + step.id()`，但类型不匹配 ReporterAgent。需要改为直接使用 `NOOP_REPORTER`：

```java
    @Bean
    ResearchNode reporterNode() {
        return new ReporterNode(NOOP_REPORTER, null, null);
    }
```

- [ ] **Step 2: 检查其他受影响文件**

搜索所有可能受接口变更影响的文件：

```bash
grep -rn "reporterAgent.report(" src/test/ --include="*.java"
grep -rn "ReporterAgent" src/test/ --include="*.java"
grep -rn "implements ReporterAgent" src/ --include="*.java"
```

除 `GraphResearchRunnerTest.java` 外不应有其他调用处。

- [ ] **Step 3: 更新 UserProfilePropertiesTest**

新增 `guideReporter` 默认值测试和 setter 测试。当前测试文件有 4 个测试方法，新增 1 个测试方法。

在测试类中新增：

```java
    @Test
    void defaultGuideReporterIsTrue() {
        UserProfileProperties properties = new UserProfileProperties();
        assertThat(properties.isGuideReporter()).isTrue();
    }
```

同时修改 `settersOverrideDefaults` 测试方法，新增 `properties.setGuideReporter(false)` 和对应的断言：

修改前：

```java
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
```

修改后：

```java
    @Test
    void settersOverrideDefaults() {
        UserProfileProperties properties = new UserProfileProperties();
        properties.setEnabled(false);
        properties.setMaxMessagesForExtraction(5);
        properties.setCacheMinutes(30);
        properties.setGuideReporter(false);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getMaxMessagesForExtraction()).isEqualTo(5);
        assertThat(properties.getCacheMinutes()).isEqualTo(30);
        assertThat(properties.isGuideReporter()).isFalse();
    }
```

- [ ] **Step 4: 运行 UserProfilePropertiesTest**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B '-Dtest=UserProfilePropertiesTest' test 2>&1 | Select-Object -Last 8
# Expected: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS
```

- [ ] **Step 5: 运行全量测试确保无回归**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'; mvn -B test 2>&1 | Select-String -Pattern "Tests run:.*Failures|BUILD" | Select-Object -Last 5
# Expected: Tests run: ~142, Failures: 0, Errors: 约 12-19（PostgreSQL 预存问题）
```

- [ ] **Step 6: 提交**

```bash
git add src/test/java/top/lanshan/manmu/config/UserProfilePropertiesTest.java src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java && git commit -m "测试适配 Phase 3：UserProfilePropertiesTest 新增 guide-reporter 测试，GraphResearchRunnerTest 适配 ReporterNode 新构造参数"
```

注意：如果 Step 1 中发现有其他测试文件受影响（如 ReporterAgent 的 mock 实现），也需要在此任务中修复并一起提交。

---

### Task 8: 全链路验证（真实 API 调用）

**Files:**
- Create: `target/http-check/phase3/request.json`（验证用临时文件）

- [ ] **Step 1: 清理旧数据并插入测试对话**

确认 PostgreSQL 运行正常：

```bash
docker ps --filter name=manmu-postgres --format "{{.Names}} {{.Status}}"
```

清理旧数据并插入测试消息：

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "
DELETE FROM conversation_messages WHERE session_id = 'test-profile-v3';
DELETE FROM user_profiles WHERE session_id = 'test-profile-v3';
INSERT INTO conversation_messages (id, session_id, thread_id, role, content, created_at)
VALUES (gen_random_uuid(), 'test-profile-v3', 'test-profile-v3-thread', 'USER',
'我是 Python 初学者，之前只用过 Excel 做数据分析，现在想用 Python 做。请用简单易懂的方式解释。',
NOW());
"
```

- [ ] **Step 2: 启动后端服务**

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'
Start-Process -FilePath "mvn" -ArgumentList '-B', 'spring-boot:run', '-Dspring-boot.run.profiles=real-model', '-Dspring-boot.run.arguments=--server.port=18080' -RedirectStandardOutput "target/phase3-run.log" -RedirectStandardError "target/phase3-run-err.log" -NoNewWindow
```

等待 ~20 秒确认启动：

```bash
sleep 20 && grep "Started DeepResearchMvpApplication" target/phase3-run.log
```

- [ ] **Step 3: 执行研究请求**

```bash
mkdir -p target/http-check/phase3
```

写入请求体：

```bash
echo '{"query":"Python 数据分析入门需要学什么？","session_id":"test-profile-v3","enable_deepresearch":true}' > target/http-check/phase3/request.json
```

发送请求（使用 `/chat/stream` 端点以确保 sessionId 正确传递）：

```bash
curl.exe -s -N -X POST http://localhost:18080/chat/stream \
  -H "Content-Type: application/json" \
  --data-binary "@target/http-check/phase3/request.json" \
  > target/http-check/phase3/response.sse 2>&1 &
```

使用 Monitor 等待完成：

```bash
until grep -q '"done":true' target/http-check/phase3/response.sse 2>/dev/null; do sleep 3; done
echo "COMPLETE"
```

- [ ] **Step 4: 验证 user_profiles 表结构化字段**

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "SELECT profile_summary, expertise_level, detail_preference, style_preference FROM user_profiles WHERE session_id = 'test-profile-v3';"
# Expected:
# - profile_summary: 包含对用户角色的描述
# - expertise_level: beginner
# - detail_preference: 非空（concise/balanced/comprehensive 之一）
# - style_preference: 非空（practical/theoretical/mixed 之一）
```

- [ ] **Step 5: 验证两次请求间画像被缓存（不会重复调用 LLM）**

发送第二次请求（同一个 sessionId）：

```bash
echo '{"query":"Python pandas 怎么入门？","session_id":"test-profile-v3","enable_deepresearch":true}' > target/http-check/phase3/request2.json

curl.exe -s -N -X POST http://localhost:18080/chat/stream \
  -H "Content-Type: application/json" \
  --data-binary "@target/http-check/phase3/request2.json" \
  > target/http-check/phase3/response2.sse 2>&1 &
```

等待完成后，检查 user_profiles 表中 session_id 为 `test-profile-v3` 的记录数：

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "SELECT count(*) FROM user_profiles WHERE session_id = 'test-profile-v3';"
# Expected: 1（只有一条，第二次请求命中缓存，没有重新提取）
```

- [ ] **Step 6: 停止服务并清理测试数据**

```powershell
$port = 18080
$owner = (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue).OwningProcess
if ($owner) { Stop-Process -Id $owner -Force }
```

```bash
docker exec manmu-postgres psql -U manmu -d manmu -c "DELETE FROM conversation_messages WHERE session_id = 'test-profile-v3'; DELETE FROM user_profiles WHERE session_id = 'test-profile-v3';"
```

---

## Phase 3 完成标准

1. ✅ `user_profiles` 表新增 3 列：`expertise_level`、`detail_preference`、`style_preference`
2. ✅ `mvn compile` 通过
3. ✅ `mvn test` 通过（约 142 tests, 0 failures, 预期约 12-19 errors 为 PostgreSQL 预存问题）
4. ✅ 全链路验证：user_profiles 表中结构化字段写入正确（expertise_level=beginner）
5. ✅ 缓存验证：同一 session 二次请求命中缓存，不重复提取
6. ✅ Reporter prompt 中注入用户画像，报告更贴合用户水平
7. ✅ 所有文件已创建或修改，全部提交
