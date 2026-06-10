# Skill 开发指南

本文说明 M-Agent 当前支持的 Skill 包结构、元数据、导入导出、启停、健康检查和 Jar 插件接口。

## Skill 类型

当前支持两类 Skill：

- Prompt Skill：由 `skill.json` 和 `SKILL.md` 组成。适合把某类任务封装为可复用提示词模板。
- Jar Skill：由 `skill.json` 和 `plugin.jar` 组成。适合需要 Java 代码执行的本地可信插件，默认关闭。

Skill 市场后端接口位于：

- `src/main/java/top/lanshan/manmu/skill/service/SkillController.java`
- `src/main/java/top/lanshan/manmu/skill/service/SkillService.java`
- `src/main/java/top/lanshan/manmu/skill/plugin/*`

内置 Skill 位于：

- `src/main/java/top/lanshan/manmu/skill/content/{skill-name}/`

本地安装 Skill 默认写入：

- `.local/skills/installed/{skill-name}/`

## 元数据字段

每个 `skill.json` 建议包含：

```json
{
  "name": "weather-now",
  "description": "查询指定城市实时天气",
  "version": "1.0.0",
  "enabled": true,
  "parameters": {
    "type": "object",
    "properties": {
      "location": {
        "type": "string",
        "description": "城市名、Location ID 或 lon,lat 经纬度"
      }
    },
    "required": ["location"]
  },
  "dependencies": ["mcp-qweather"],
  "category": "工具",
  "tags": ["weather", "mcp"]
}
```

核心字段：

- `name`：唯一名称，建议使用小写字母、数字和短横线。
- `description`：说明 Skill 能力，控制台和模型提示都会用到。
- `version`：语义化版本，例如 `1.0.0`。
- `enabled`：是否启用。
- `parameters`：JSON Schema 风格参数说明，会作为 Tool 输入 schema。
- `dependencies`：依赖标识，例如 `mcp-qweather`、`mcp-amap`。
- `category`、`tags`：可选，用于控制台筛选和展示。

## Prompt Skill

### 目录结构

```text
my-skill/
  skill.json
  SKILL.md
```

`SKILL.md` 是提示词模板，可以使用 `{{参数名}}` 占位。

示例：

```markdown
# 天气查询

请查询以下地点的实时天气：

- 地点：{{location}}
- 单位：{{unit}}

请优先使用真实 MCP 工具返回，不要编造天气。
```

### 打包

Prompt Skill 上传包是 zip，根目录包含：

```text
skill.json
SKILL.md
```

导入接口：

```powershell
curl.exe -F "file=@target/demo-packages/my-skill.zip" http://localhost:18080/api/skills/packages/import
```

导出接口：

```powershell
curl.exe -OJ http://localhost:18080/api/skills/my-skill/export
```

## Jar Skill

Jar Skill 适合本地可信 Java 插件。默认配置为关闭：

```yaml
mvp:
  skill:
    jar-plugins:
      enabled: false
```

仅在本地可信演示时启用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080 --mvp.skill.jar-plugins.enabled=true"
```

### 包结构

Jar Skill 上传包是 zip，根目录包含：

```text
skill.json
plugin.jar
README.md   # 可选
```

`skill.json` 中 `packageType` 可以省略，导入后会被设置为 `JAR`。

### 插件接口

Jar 中必须实现：

```java
package example;

import top.lanshan.manmu.skill.plugin.SkillPlugin;
import top.lanshan.manmu.skill.plugin.SkillPluginContext;
import top.lanshan.manmu.skill.service.SkillDefinition;

import java.util.Map;

public class EchoSkill implements SkillPlugin {
    @Override
    public SkillDefinition definition() {
        SkillDefinition definition = new SkillDefinition();
        definition.setName("echo-skill");
        definition.setDescription("回显输入参数");
        definition.setVersion("1.0.0");
        return definition;
    }

    @Override
    public String execute(Map<String, Object> input, SkillPluginContext context) {
        return "echo=" + input;
    }
}
```

Jar 中还必须包含 ServiceLoader 声明文件：

```text
META-INF/services/top.lanshan.manmu.skill.plugin.SkillPlugin
```

文件内容是一行实现类全名：

```text
example.EchoSkill
```

### 生命周期

后端会通过独立 `SkillPluginClassLoader` 加载 `plugin.jar`。启用、重载、卸载时会注册或关闭插件实例。

注意：

- ClassLoader 隔离不是安全沙箱。
- 不要上传不可信 Jar。
- 插件不要读取或输出 `.local/` 中的敏感 Key。

## 管理接口

列出 Skill：

```powershell
curl.exe http://localhost:18080/api/skills
```

查看详情：

```powershell
curl.exe http://localhost:18080/api/skills/weather-now
```

健康检查：

```powershell
curl.exe http://localhost:18080/api/skills/weather-now/health
```

启停：

```powershell
curl.exe -X PATCH http://localhost:18080/api/skills/weather-now/toggle
```

重载：

```powershell
curl.exe -X POST http://localhost:18080/api/skills/weather-now/reload
```

调用历史：

```powershell
curl.exe http://localhost:18080/api/skills/weather-now/invocations
```

卸载本地包：

```powershell
curl.exe -X DELETE http://localhost:18080/api/skills/packages/my-skill
```

## 在聊天中调用

显式调用格式：

```text
@weather-now --location=上海 查询实时天气
```

也可以在普通问题中让模型根据 Skill 摘要自动选择工具。显式 `@skill-name` 更适合演示和验收。

## 开发检查清单

- `skill.json` 名称唯一，字段完整。
- `parameters` 是合法 JSON 对象。
- Prompt Skill 包含 `SKILL.md`。
- Jar Skill 包含 `plugin.jar` 和 ServiceLoader 声明。
- 依赖 MCP 时在 `dependencies` 中写明，例如 `mcp-qweather`。
- 导入后通过 `/api/skills/{name}/health` 检查。
- 显式 `@skill-name` 通过 `/chat/stream` 做真实调用验证。
