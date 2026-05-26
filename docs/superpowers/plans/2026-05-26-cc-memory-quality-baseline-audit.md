# CC 记忆与用户画像工程质量基线审计

审计时间：2026-05-26

## 工作区状态

- `git status --short` 显示已有未提交改动：`AGENTS.md`、`CLAUDE.md`。
- 本阶段不修改上述文件，不覆盖既有改动。
- 未读取、打印或提交 `.local/model-providers.json`。
- 未修改 `.claude/`。

## 静态审计结论

### 已确认修复或基本对齐

- 迁移目录中已存在：
  - `V4__create_conversation_messages.sql`
  - `V5__create_user_profiles.sql`
  - `V6__alter_user_profiles.sql`
- `V6__alter_user_profiles.sql` 已为 `user_profiles` 增加：
  - `expertise_level`
  - `detail_preference`
  - `style_preference`
- `UserProfileEntity` 的 `@Column` 字段与 `V5` + `V6` 后的表结构一致。
- `UserProfileRecord` 的 JSON 字段名与结构化字段一致。
- `ReporterAgent` 当前生产接口为 `report(ResearchState state, String userProfileContext)`，`LlmReporterAgent` 与 `ReporterNode` 已适配该签名。

### 必修问题

- `ConversationMemoryService.saveMessage(...)` 尚未接入 `GraphResearchRunner` 或 `ChatController` 主链路；`/chat/stream`、`/chat/resume` 当前不会稳定写入 USER 与最终 ASSISTANT 消息。
- `UserProfileService` 当前直接 `.block()` 查询 R2DBC 仓库和保存画像。虽然 `GraphResearchRunner` 的图执行链路使用 `subscribeOn(Schedulers.boundedElastic())`，但服务本身没有响应式接口或清晰边界说明。
- `UserProfileService` 的 JSON 解析降级较粗：
  - `ObjectMapper` 是静态实例，不利于测试替换。
  - 非法 JSON 会把原始模型输出写入 `profile_summary`，长度和内容不可控。
  - `expertise_level`、`detail_preference`、`style_preference` 没有枚举规范化。
  - 缺少 `profile_summary` 时没有稳定 fallback。
- `formatProfileContext(...)` 直接拼接字段，存在空摘要但仍拼出局部上下文、标点不稳定等可维护性问题。
- 当前测试矩阵不足以覆盖记忆写入失败不阻断主流程、画像提取失败降级、缓存命中不调用模型、未知枚举值规范化等风险。

### 可延后问题

- 是否将 `UserProfileService` 完整改为响应式链路可以在加固阶段评估；若影响面过大，可先保留同步方法并明确只在 boundedElastic 图执行链路中调用。
- Coordinator 与 Reporter 的画像注入文案目前不一致，可在后续阶段统一为明确的 prompt 边界。
- 文档计划与当前代码状态存在偏差：计划中提到缺失 `V6`，但当前代码树已存在该迁移；后续需要在阶段 7 同步计划状态。

## 阶段 0 验收

- 已静态确认原计划中的 schema drift 风险在当前代码树中已被部分修复：`V6` 已存在，实体字段与迁移字段一致。
- 已确认更高优先级的剩余风险集中在主流程记忆写入、画像服务降级、测试覆盖和真实 E2E 验证。
- 本阶段未修改业务代码。
