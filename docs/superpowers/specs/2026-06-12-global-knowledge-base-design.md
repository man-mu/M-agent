# 全局知识库 & 用户画像设计文档

> 日期：2026-06-12
> 方案：A — 全局 session_id 标记

## 一、需求概述

1. **全局知识库**：用户可在独立页面上传全局文档，所有会话共享，用于 RAG 语义检索。
2. **用户画像展示与手动覆盖**：展示系统自动构建的用户画像，支持手动编辑各字段，手动值优先于自动提取。
3. **页面位置**：顶部导航新增"知识库"标签，路由 `/knowledge`。

## 二、数据模型变更

### 2.1 RAG 全局文档

现有 `vector_store`（pgvector）表的 metadata 新增 `scope` 字段：

| metadata 字段 | 值 | 含义 |
|---|---|---|
| session_id | 实际会话 ID 或 `__global__` | 文档归属 |
| scope | `"session"` 或 `"global"` | 文档作用域 |
| user_id | 用户 ID | 不变 |

新增 `rag_documents` 元数据表（Flyway 迁移）：

```sql
CREATE TABLE rag_documents (
    id UUID PRIMARY KEY,
    file_name VARCHAR(500) NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'session',
    session_id VARCHAR(100),
    user_id VARCHAR(100),
    chunks INT NOT NULL DEFAULT 0,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rag_documents_scope ON rag_documents(scope);
CREATE INDEX idx_rag_documents_session ON rag_documents(session_id);
```

### 2.2 用户画像

现有 `user_profiles` 表新增两列（Flyway 迁移）：

```sql
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'session';
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS manual_fields TEXT NOT NULL DEFAULT '[]';
CREATE INDEX IF NOT EXISTS idx_user_profiles_scope ON user_profiles(scope);
```

全局画像用 `scope='global'` + `session_id='__global__'` 存储，全局唯一。

`manual_fields` 存储 JSON 数组，记录用户手动覆盖的字段名，如 `["expertise_level", "style_preference"]`。

**自动提取保护规则**：
- 自动提取画像时，读取 `manual_fields`
- 手动标记过的字段不被自动提取结果覆盖
- 未标记的字段正常由自动提取更新

## 三、后端 API

### 3.1 全局知识库

扩展现有 `RagDataController`：

| 端点 | 方法 | 说明 |
|---|---|---|
| `/api/rag/upload?scope=global` | POST | 上传全局文档，写入 pgvector 时 metadata 带 `scope=global`，同时写入 `rag_documents` 表 |
| `/api/rag/documents?scope=global` | GET | 列出全局已上传的文档 |
| `/api/rag/documents/{id}` | DELETE | 删除指定全局文档及其关联的向量块 |

**GET /api/rag/documents?scope=global 返回**：

```json
{
  "documents": [
    {
      "id": "uuid",
      "fileName": "我的爱好.txt",
      "chunks": 3,
      "uploadedAt": "2026-06-12T14:30:00Z"
    }
  ]
}
```

**DELETE /api/rag/documents/{id}**：
1. 从 `rag_documents` 表删除记录
2. 从 pgvector 删除对应 `scope=global` + `original_filename` 匹配的向量块

### 3.2 用户画像

新增 `UserProfileController`：

| 端点 | 方法 | 说明 |
|---|---|---|
| `/api/user-profile` | GET | 获取全局画像 |
| `/api/user-profile` | PUT | 更新全局画像（手动覆盖） |
| `/api/user-profile/reset` | POST | 重置手动覆盖，恢复自动提取值 |

**GET /api/user-profile 返回**：

```json
{
  "profile_summary": "资深 Java 开发者",
  "expertise_level": "advanced",
  "detail_preference": "balanced",
  "style_preference": "practical",
  "manual_fields": ["profile_summary", "expertise_level"],
  "updated_at": "2026-06-12T14:30:00Z"
}
```

**PUT /api/user-profile 请求体**：

```json
{
  "profile_summary": "资深 Java 开发者",
  "expertise_level": "advanced",
  "manual_fields": ["profile_summary", "expertise_level"]
}
```

PUT 逻辑：
1. 查找或创建 `scope=global` 的画像记录
2. 更新传入的字段值
3. 更新 `manual_fields` 为请求中的列表

**POST /api/user-profile/reset**：
- 清空 `manual_fields` 为 `[]`
- 不修改画像字段值（下次自动提取时会用最新对话数据覆盖全部字段）

## 四、前端页面

### 4.1 导航栏

顶部 `a-segmented` 新增"知识库"标签，图标 `DatabaseOutlined`，路由 `/knowledge`。

导航顺序：`对话 | Skill | MCP 工具 | 知识库 | 模型`

`layout/index.vue` 的 `navItems` 和 `switchMode` 需要新增对应逻辑。
`AppCapabilities` 接口无需新增开关 — 只要 RAG 启用就显示知识库标签。

### 4.2 知识库页面

新建 `ui-vue3/src/views/knowledge/index.vue`，分上下两个区块：

**区块一：全局知识库**

- 标题 + 上传按钮
- 文档列表：文件名、切块数、上传时间、删除按钮
- 空态提示："尚未上传全局文档"
- 上传复用 `ragUpload.ts` 校验逻辑（10MB 限制、文件类型提示）
- 上传接口调用 `/api/rag/upload?scope=global`

**区块二：用户画像**

- 查看模式：只读展示 4 个字段 + 来源标注（手动/自动）
- 编辑模式：文本字段变为输入框，枚举字段变为 `a-select` 下拉
- 编辑/取消/保存按钮
- 重置按钮：调用 `/api/user-profile/reset`

### 4.3 新增前端文件

| 文件 | 说明 |
|---|---|
| `ui-vue3/src/views/knowledge/index.vue` | 知识库页面主组件 |
| `ui-vue3/src/services/api/knowledge.ts` | 知识库 API 服务（文档 CRUD + 画像 CRUD） |

修改的文件：

| 文件 | 改动 |
|---|---|
| `ui-vue3/src/components/layout/index.vue` | navItems 新增知识库、switchMode 新增路由 |
| `ui-vue3/src/router/defaultRoutes.ts` | 新增 `/knowledge` 路由 |
| `ui-vue3/src/services/api/rag.ts` | uploadDocument 支持 scope 参数 |

### 4.4 与聊天页面的联动

- 聊天页 RAG 上传面板保留（会话级上传不变）
- RAG 检索时自动合并全局 + 会话级文档
- 画像自动提取仍按现有逻辑在对话后触发，但跳过 `manual_fields` 中标记的字段

## 五、错误处理与边界情况

### 5.1 全局知识库

| 场景 | 处理 |
|---|---|
| RAG 未启用 | 知识库页面显示"RAG 未启用"提示，上传按钮禁用 |
| 上传超过 10MB | 返回 413，前端提示文件过大 |
| 删除文档 | 同步删除 pgvector 中对应向量块 |
| 全局文档过多 | 列表限制 50 条，防止检索噪音 |

### 5.2 用户画像

| 场景 | 处理 |
|---|---|
| 画像为空（新用户） | 显示"暂无画像信息，开始对话后系统会自动构建" |
| 手动覆盖后自动提取 | 已标记 `manual_fields` 的字段不覆盖，其余正常更新 |
| 重置手动覆盖 | `manual_fields` 清空，下次自动提取覆盖全部字段 |
| 并发写入 | 最后写入者胜（单用户场景） |

### 5.3 RAG 检索合并策略

`RagRetriever.retrieve()` 修改为两次查询合并：
1. 查询 `scope=global` 的文档
2. 查询 `session_id=当前会话` 的文档
3. 合并结果，按文档内容去重

如果 pgvector metadata filter 不支持简单 OR 语义，则拆为两次 `similaritySearch` 调用后合并。

## 六、Flyway 迁移

新增 V9 迁移文件：`V9__add_global_knowledge_and_profile_manual_fields.sql`

### PostgreSQL 版本（`classpath:db/migration`）

```sql
-- 全局文档元数据表
CREATE TABLE rag_documents (
    id UUID PRIMARY KEY,
    file_name VARCHAR(500) NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'session',
    session_id VARCHAR(100),
    user_id VARCHAR(100),
    chunks INT NOT NULL DEFAULT 0,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rag_documents_scope ON rag_documents(scope);
CREATE INDEX idx_rag_documents_session ON rag_documents(session_id);

-- 用户画像新增列
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'session';
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS manual_fields TEXT NOT NULL DEFAULT '[]';
CREATE INDEX IF NOT EXISTS idx_user_profiles_scope ON user_profiles(scope);
```

### H2 版本（`classpath:db/h2-migration`）

与 PostgreSQL 版本基本一致，差异：
- `TIMESTAMP WITH TIME ZONE` → `TIMESTAMP`
- H2 的 `ALTER TABLE ADD COLUMN IF NOT EXISTS` 需逐列单独执行（已符合上述写法）
