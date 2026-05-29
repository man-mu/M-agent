# MCP 工具配置

当前项目默认接入国内厂商高德地图 MCP Server，用于覆盖日常生活场景中的天气、地址、周边设施、路线和距离查询。

## 开通 Key

1. 登录高德开放平台。
2. 创建应用并添加 Key。
3. Key 的服务平台选择 `Web 服务`。
4. 本地启动后端前设置环境变量：

```powershell
$env:AMAP_MAPS_API_KEY="你的高德 Web 服务 Key"
```

## 当前暴露的 10 个工具

| 工具名 | 日常用途 |
| --- | --- |
| `maps_weather` | 查询城市天气 |
| `maps_geo` | 地址转经纬度 |
| `maps_regeo` | 经纬度转地址 |
| `maps_text_search` | 关键词搜索地点、商户、设施 |
| `maps_around_search` | 搜索附近餐饮、医院、景点、充电站等 |
| `maps_ip_location` | 根据 IP 判断大致城市位置 |
| `maps_direction_driving` | 驾车路线规划 |
| `maps_direction_walking` | 步行路线规划 |
| `maps_direction_bicycling` | 骑行路线规划 |
| `maps_distance` | 计算两点或多点间距离 |

配置文件位于 `src/main/resources/mcp-config.json`。项目会从 `AMAP_MAPS_API_KEY` 环境变量解析 Key，避免把密钥写入仓库。
