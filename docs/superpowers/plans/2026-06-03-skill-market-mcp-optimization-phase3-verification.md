# Skill 市场与 MCP 管理优化 Phase 3 验证记录

验证时间：2026-06-04 13:58 +08:00

## 范围

- 验证计划 `2026-06-03-skill-market-mcp-optimization.md` 的 Phase 3：`/skills` 从 CRUD 页面升级为本地 Skill 市场控制台。
- 覆盖已安装、本地市场、导入记录、Skill 详情抽屉、市场元数据、导入、启停、导出、卸载，以及桌面/移动端布局。
- 本记录不包含 `.local/model-providers.json`、`.local/mcp-keys.json` 或任何 API Key。

## 自动化验证

前端聚焦测试：

```powershell
cd ui-vue3
npm run test:unit -- skillForm.spec.ts skillMarket.spec.ts
```

结果：

- `skillForm.spec.ts` 与 `skillMarket.spec.ts` 共 10 个测试通过。
- 覆盖 Skill 名称校验、homepage 元数据校验、依赖/标签辅助逻辑、本地/内置 Skill 标签、市场筛选、状态筛选和搜索文本。

前端生产构建：

```powershell
cd ui-vue3
npm run build
```

结果：

- `vue-tsc --build --force` 通过。
- `vite build` 通过。

## 真实 HTTP / 浏览器验证

环境：

- Docker Desktop 中 PostgreSQL 容器 `manmu-postgres` 处于 healthy 状态，`5432` 映射到 Windows localhost。
- 启动前 `18080`、`5173` 均无监听。
- 后端使用 `C:\WorkResources\JDKs\JDK17\bin\java.exe` 启动到 `18080`，profile 为 `real-model`，并通过环境变量临时关闭 RAG：`MVP_RAG_ENABLED=false`。
- 后端日志确认使用 Java 17.0.17，连接 PostgreSQL 17.9，Flyway 8 个迁移校验通过，内置 Skill 从源码目录加载，本地市场目录为 `.local/skills/installed`。
- 前端通过 Vite 启动到 `5173`，代理后端 `http://localhost:18080`。

准备的测试包：

- `target/http-check/phase3-market-skill.zip`
- zip 内容只有 `skill.json` 和 `SKILL.md`。
- Skill 名称为 `phase3-market-skill`，类型为 Prompt Skill。
- 元数据包含 `displayName`、`category`、`author`、`homepage`、`tags`、`packageType`，用于验证市场详情展示。

API 验证：

```powershell
curl.exe -F "file=@target/http-check/phase3-market-skill.zip" http://localhost:18080/api/skills/packages/import
curl.exe http://localhost:18080/api/skills/phase3-market-skill
curl.exe -L http://localhost:18080/api/skills/phase3-market-skill/export -o target/http-check/phase3-exported-skill.zip
curl.exe -X PATCH http://localhost:18080/api/skills/phase3-market-skill/toggle
curl.exe -X PATCH http://localhost:18080/api/skills/phase3-market-skill/toggle
```

结果：

- 导入返回 `storageLocation=LOCAL`、`packageType=PROMPT`、`enabled=true`。
- 详情接口返回市场元数据和 `promptTemplate`。
- 导出 zip 大小为 934 bytes。
- 页面和 API 启停均成功，状态可从 `enabled=true/false` 往返切换。

浏览器验证：

- 桌面视口 `1280x720` 打开 `/skills`：
  - 页面显示 Skill 总数、本地安装、内置 Skill、已启用和已停用统计。
  - `已安装`、`本地市场`、`导入记录` Tab 可见。
  - 表格可区分内置 Skill 与本地 Skill。
  - 内置 Skill 的编辑、启停、导出、重载、卸载按钮禁用。
  - 本地 Skill 的详情、编辑、启停、导出、重载、卸载按钮可用。
  - 详情抽屉展示名称、显示名、描述、版本、分类、作者、来源、状态、安装时间、更新时间、依赖、标签、参数 schema 和 Prompt 预览。
  - 页面未展示 `C:\MainData`、`.local/skills` 等本地敏感完整路径，只展示“本地市场目录”。
  - 浏览器布局指标：`scrollWidth=1280`、`clientWidth=1280`，无水平溢出。

- 本地市场 Tab：
  - 显示 `Prompt Skill Zip` 导入区域。
  - 只显示本地 Skill `phase3-market-skill`。
  - 搜索 `Codex` 后只保留该本地 Skill，内置 `code-review`、`weather-now` 不再显示。
  - 页面不暴露本地敏感完整路径。

- 移动视口 `390x844`：
  - 表格隐藏，切换为卡片列表。
  - 本地市场 Tab 显示 1 个本地 Skill 卡片。
  - 浏览器布局指标：`scrollWidth=390`、`clientWidth=390`，无水平溢出。
  - 操作按钮和卡片均未超出视口。

页面卸载验证：

- 在 `/skills` 页面点击本地 Skill 的卸载按钮。
- 弹窗标题显示 `卸载 Skill 包：phase3-market-skill`。
- 点击确认卸载后，页面本地安装数回到 0，列表中不再显示 `phase3-market-skill`。
- API 复核：

```powershell
curl.exe -s -o NUL -w "%{http_code}" http://localhost:18080/api/skills/phase3-market-skill
```

结果：

- 返回 `404`。
- `/api/skills` 只剩 `code-review`、`location-analyzer`、`weather-now` 三个内置 Skill。

浏览器截图：

- `target/http-check/phase3-skills-desktop.png`
- `target/http-check/phase3-skills-mobile.png`

## 关闭验证

- 已停止本阶段启动的 Maven/Java 后端进程。
- 已停止本阶段启动的 Vite 前端进程。
- 结束后确认端口释放：

```powershell
Get-NetTCPConnection -LocalPort 18080,5173 -ErrorAction SilentlyContinue
```

结果：

- `18080`、`5173` 无监听。
- PostgreSQL Docker 容器是验证前已有基础依赖，本阶段未停止该容器。

## Phase 3 结论

- `/skills` 可以作为本地 Skill 市场控制台使用。
- 用户可以发现、导入、查看详情、启用/禁用、导出、重载和卸载本地 Prompt Skill。
- 页面能够区分内置 Skill 与本地安装 Skill，并禁用内置 Skill 的写操作。
- 市场详情展示保留相对化来源，不泄露本地敏感完整路径或任何 Key。
- 桌面与移动端视口均无明显布局溢出。
- 自动化测试、前端构建、真实 HTTP/API 和浏览器验证均通过。
