# 项目级 Codex 指令

## 项目概况

- 这是一个 Java 17 / Maven / Spring Boot 3.4.x 项目。
- 当前项目是 DeepResearch 风格的精简后端，主包名为 `top.lanshan.manmu`。
- 后端使用 WebFlux SSE 输出研究工作流进度。
- 当前运行路径使用真实大模型调用，不保留 mock agent / mock search fallback。

## 本地命令

- 执行 Maven 命令前，优先使用 `JAVA_HOME=C:\WorkResources\JDKs\JDK17`。
- 验证命令：`mvn test`。

## 本地文件与凭证

- `.local/` 被 `.gitignore` 忽略，用于保存本地模型供应商 API Key 等敏感配置。
- `target/`、`.idea/` 被 `.gitignore` 忽略，不应提交。
- 不要把 `.local/model-providers.json` 中的 API Key 写入源码、测试断言或提交记录。
- 不用管 `.claude/` 文件夹；不要修改、删除或提交它，除非用户明确要求。

## 语言偏好

- 思考、分析和内部推理过程使用英文。
- 最终回复用户时使用中文，除非用户明确要求使用其他语言。

## 开发约束

- 新增或修改说明文件时使用中文。
- 每完成一个小的阶段任务就提交一次，commit 说明使用中文。
- 开发新功能时禁止使用 mock，必须接入真实生产环境需要的数据。
- 与 DeepResearch 主项目对齐时，优先做精简版可运行能力，不要一次性搬入 RAG、MCP、Redis、前端等完整复杂模块。
- 保持真实 API 调用路径可测；涉及 DashScope / DeepSeek 的测试依赖本地 `.local/model-providers.json` 中已有 key。
- 每次启动后端服务进行测试后，测试完毕必须关闭该服务。
