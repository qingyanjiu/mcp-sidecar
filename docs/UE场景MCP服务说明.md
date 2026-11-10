# UE 场景 MCP 服务说明

本文是 **UE 场景控制 MCP server** 的完整说明：连接方式、全部端点、20 个工具的
函数描述与参数、返回值语义、联调步骤。

> **代码是唯一事实来源。** 工具名/参数改动需要同时改
> `service/mcp/scene/SceneMcp.java`、`scene/SceneActions.java` 和本文。
> 请求格式的更多细节（报文样例、坑）见 [`场景动作下发接口.md`](./场景动作下发接口.md)。

---

## 1. 服务概览

| 项 | 值 |
|---|---|
| MCP server 名称 | `mcp-scene`（`serverInfo.version = 1.0.0`） |
| 传输方式 | **MCP Streamable HTTP**（Spring WebMvc 实现） |
| 端点 | `POST /mcp/scene` |
| 端口 | `2222`（`application-local.yml` / `application-server.yml`） |
| 鉴权 | **无**（生产环境如需收紧，应在网关层处理；同进程的 `/scene/*` 联调接口也一并考虑） |
| 工具数量 | **20**：19 个动作工具 + 1 个查询工具 |
| 动作帧出口 | WebSocket `/ws/scene`（由前端页面连接，不是 MCP 客户端） |
| 动作超时 | `scene.action-timeout-ms`，默认 **15000ms** |

MCP server 只负责“让 LLM 能调”。真正的动作帧通过 `/ws/scene` 推给场景页面，
**UE 不回执**，所以 `dispatched` 只代表帧已送到页面。

相关源码：

| 文件 | 作用 |
|---|---|
| `src/main/java/com/hx/mcpsidecar/service/mcp/scene/SceneMcp.java` | 20 个 MCP 工具的定义 |
| `src/main/java/com/hx/mcpsidecar/scene/SceneActions.java` | 动作词表、参数名、楼层语义、中文枚举 |
| `src/main/java/com/hx/mcpsidecar/config/mcp/SceneMcpServerConfig.java` | `/mcp/scene` 端点与 `X-Session-Id` 注入 |
| `src/main/java/com/hx/mcpsidecar/scene/SceneDispatcher.java` | 下发、等待回执、超时 |
| `src/main/java/com/hx/mcpsidecar/scene/SceneSessionRegistry.java` | 会话路由与单会话兜底 |
| `src/main/java/com/hx/mcpsidecar/scene/SceneWebSocketHandler.java` | `/ws/scene` 前端通道 |
| `src/main/java/com/hx/mcpsidecar/controller/SceneTestController.java` | `/scene/*` 联调接口 |

---

## 2. 连接方式

### 2.1 MCP Streamable HTTP 握手

MCP 客户端（Python / LangChain 等）按标准 Streamable HTTP 流程连接：

```
POST http://<sidecar-host>:2222/mcp/scene
Content-Type: application/json
Accept: application/json, text/event-stream
```

**① initialize**

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-03-26",
    "capabilities": {},
    "clientInfo": { "name": "python-agent", "version": "1.0" }
  }
}
```

响应头里会返回 `Mcp-Session-Id`（不同实现大小写可能不同，HTTP header 不区分大小写）。
**后续所有请求都要带回这个头。**

**② notifications/initialized**

```json
{ "jsonrpc": "2.0", "method": "notifications/initialized" }
```

**③ tools/list**

```json
{ "jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {} }
```

**④ tools/call**

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "scene_switch_floor",
    "arguments": { "floor": 3, "focus": true }
  }
}
```

### 2.2 两个 session id 不要混

| Header | 谁生成 | 作用 |
|---|---|---|
| `Mcp-Session-Id` | MCP SDK，`initialize` 时返回 | MCP 协议层会话跟踪，与业务无关 |
| `X-Session-Id` | 前端页面在 WS `hello` 里声明 | **业务路由键**：决定动作推给哪个页面 |

- `X-Session-Id` 必须与前端 `hello` 里的 `sessionId` **逐字一致**。
- **这个值绝不能由 LLM 填**，必须由 agent 按用户身份注入。
- 两个头同时存在，互不影响。`X-Session-Id` 由
  `SceneMcpServerConfig` 的 `contextExtractor` 提取，工具签名里看不到它。

### 2.3 `X-Session-Id` 缺失时的兜底

| 情况 | 行为 |
|---|---|
| 带了 header，命中连接 | 正常下发 |
| 带了 header，没命中 | ❌ 立刻失败“场景页面未连接” |
| 没带 header，**恰好 1 条**连接 | ⚠️ 路由到那一条（`scene.single-session-fallback=true`，默认开） |
| 没带 header，**2 条以上**连接 | ❌ 拒绝猜测，直接失败 |

兜底只给单用户联调用。**多用户上线前必须保证每个请求都带 `X-Session-Id`**，
否则可能推到别人的浏览器。

### 2.4 Python 侧的注意点

现有 `agentic-app/mcp_client/mcp_loader.py` 用 `MultiServerMCPClient`
在启动时一次性加载工具，header 只能是静态配置，**无法按用户/请求注入
`X-Session-Id`**。接入场景服务时建议走 `direct_client` 模式
（参考 `mcp_client/knowledge_mcp_client.py`），自己维护连接，并在每次
`tools/call` 的 HTTP 请求头里带上当前用户的 `X-Session-Id`。

配置条目形态：

```yaml
scene:
  transport: streamable_http
  url: http://10.204.146.228:2222/mcp/scene
  direct_client: true          # 由专用客户端接管，按请求注入 X-Session-Id
```

> 若只是单人联调，可以先不加 header，靠单会话兜底跑通；但上线前必须补上。

### 2.5 超时

sidecar 下发动作后会等待页面回执，上限 `scene.action-timeout-ms`（默认 15s）。
**Python 侧 MCP client 的超时必须大于 15s**，否则外层先断，工具拿到的是
“客户端已放弃”而不是干净的超时结果。

---

## 3. 全部端点

| 端点 | 方法 | 使用者 | 说明 |
|---|---|---|---|
| `/mcp/scene` | POST | agent / LLM | MCP Streamable HTTP，20 个工具 |
| `/scene/dispatch` | POST / GET | 人（联调、手测） | 直接下发一个动作，走同一条 dispatch 链路 |
| `/scene/sessions` | GET | 人 | 当前连着的场景页面列表（拿 `sessionId`） |
| `/scene/actions` | GET | 人 | 动作词表、破坏性动作、楼层语义 |
| `/ws/scene` | WebSocket | 前端场景页面 | 接收 `hello` / `scene_state` / `action_result`，接收动作帧 |
| `/actuator/health` | GET | 运维 | 健康检查，管理端口 **9970** |

### 3.1 `/scene/dispatch`（联调用）

POST JSON body：

```json
{
  "action": "scene_switch_floor",
  "sessionId": "dev-1",
  "params": { "floor": 3, "focus": true }
}
```

GET query 版（`params` 必须是 **URL 编码后的 JSON 字符串**）：

```
GET /scene/dispatch?action=scene_switch_floor&sessionId=dev-1&params=%7B%22floor%22%3A3%7D
```

成功 `200`，失败 `409`：

```json
{ "action": "scene_switch_floor", "sessionId": "dev-1",
  "params": { "floor": 3 }, "ok": true, "state": "dispatched" }
```

### 3.2 `/scene/sessions`

返回当前已连接的页面：

```json
[
  {
    "sessionId": "dev-1",
    "userId": "u-42",
    "pendingActions": 0,
    "sceneState": { "type": "scene_state", "sessionId": "dev-1",
                    "ready": true, "floor": 11, "build": "科研办公楼" }
  }
]
```

### 3.3 `/scene/actions`

```json
{
  "count": 19,
  "actions": ["scene_switch_floor", "..."],
  "destructive": ["scene_set_area_state", "scene_set_inspection_state"],
  "floorSemantics": "楼层为整数：-1=B1，1~N 为具体楼层，99=整栋楼。..."
}
```

### 3.4 `/ws/scene`（前端通道）

页面连上后必须先发：

```json
{ "type": "hello", "sessionId": "dev-1", "userId": "u-42" }
```

`sessionId` 为空会被直接断开；同一 `sessionId` 重连会顶掉旧连接。
MCP 客户端**不要连这个端点**，它是给场景页面用的。

---

## 4. 工具总览

| # | 工具名 | 类别 | 必填参数 | 可选参数 | 破坏性 |
|---|---|---|---|---|---|
| 1 | `scene_switch_floor` | 楼层/视角 | `floor` | `focus` | 否 |
| 2 | `scene_reset_view` | 楼层/视角 | — | — | 否 |
| 3 | `scene_recover_view` | 楼层/视角 | — | — | 否 |
| 4 | `scene_close_detail` | 楼层/视角 | — | — | 否 |
| 5 | `scene_reset_all` | 楼层/视角 | — | — | 否 |
| 6 | `scene_show_visitor_track` | 组合动作 | — | `floor` | 否 |
| 7 | `scene_show_camera` | 组合动作 | `tag` | `floor`, `app`, `stream`, `name`, `source_url` | 否 |
| 8 | `scene_show_inspection` | 组合动作 | — | `floor` | 否 |
| 9 | `scene_show_meeting_rooms` | 组合动作 | — | `floor` | 否 |
| 10 | `scene_show_dining` | 组合动作 | — | `floor` | 否 |
| 11 | `scene_show_area_overview` | 组合动作 | — | `floor`, `tags` | 否 |
| 12 | `scene_show_device_category` | 组合动作 | `category` | `floor` | 否 |
| 13 | `scene_show_fusion` | 组合动作 | `tag` | `build`, `floorNum`, `options` | 否 |
| 14 | `scene_focus_actor` | 原子动作 | `actorType`, `type`, `tag` | — | 否 |
| 15 | `scene_set_actor_visible` | 原子动作 | `actorType`, `type`, `tag`, `visible` | — | 否 |
| 16 | `scene_set_poi_visible` | 原子动作 | `type`, `build`, `floorNum`, `visible`※ | — | 否 |
| 17 | `scene_set_env` | 原子动作 | — | `env_time`, `env_weather` | 否 |
| 18 | `scene_set_area_state` | 破坏性 | `tag`, `state` | — | 是（默认关闭） |
| 19 | `scene_set_inspection_state` | 破坏性 | `tag`, `state` | — | 是（默认关闭） |
| 20 | `get_scene_state` | 查询 | — | — | 否 |

> ※ `scene_set_poi_visible` 的 `type` / `build` / `floorNum` 在 schema 里标了必填，
> 但只在 `visible=true` 时使用；`visible=false` 时服务端会忽略它们，直接隐藏全部 POI。

**楼层语义（适用于所有带 `floor` 的工具）：**

```
-1 = B1
1 ~ N = 具体楼层
11 / 99 = 整栋楼（11 会让"楼栋"按钮高亮，99 不高亮；效果一样）
```

不要用 `1` 表示“室外”，不要用 `0` 表示“整栋楼”。

---

## 5. 工具详情

> “描述”一栏是 `@McpTool.description` 的原文，也就是 LLM 实际看到的工具说明。

### 5.1 楼层 / 视角

#### 1. `scene_switch_floor`

**描述：** 切换到指定楼层（可选把视角聚焦过去）。用户说“切到 N 楼”“去 N 层”“看整栋楼”“切到室外”时用它。

**参数：**

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `floor` | int | ✅ | — | 目标楼层，见楼层语义 |
| `focus` | bool | ⬜ | true | 是否把相机聚焦到该楼层 |

---

#### 2. `scene_reset_view`

**描述：** 重置相机视角到初始位置（回到默认全景）。用户说“重置视角”“回到默认视角”“复位”时用它。

无参数。

---

#### 3. `scene_recover_view`

**描述：** 恢复上一个视角。用户说“返回”“退回去”“恢复视角”时用它。

无参数。

---

#### 4. `scene_close_detail`

**描述：** 关闭当前打开的业务弹窗并收回视角（相当于用户手动点弹窗的关闭按钮）。用户说“关掉”“关闭弹窗”“退出详情”时用它。

无参数。

---

#### 5. `scene_reset_all`

**描述：** 全部重置：视角回到楼栋全景、关掉所有专题图层和业务弹窗，把场景清回初始状态。用户说“清场”“全部关掉”“回到初始状态”“把刚才显示的都关了”时用它。

无参数。

**已知缺口：**
- 关不掉 `scene_show_fusion` 打开的虚实融合窗（`HideActorById` 的 id 运行时生成，无法枚举）。
- 有专题页正挂载时，它可能 `watch(appState.floor)` 把 POI 填回来。

---

### 5.2 组合动作

#### 6. `scene_show_visitor_track`

**描述：** 展示访客轨迹：切到楼层、高亮并聚焦访客对象、打开访客卡。用户说“看访客”“访客轨迹”“访客去哪了”时用它。

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `floor` | int | ⬜ | 1 | 访客所在的楼层 |

---

#### 7. `scene_show_camera`

**描述：** 展示某路监控：切到楼层、聚焦该摄像头点位、打开视频窗。用户说“看监控”“调出摄像头”“看 XX 的监控”时用它。`tag` 是摄像头点位标识（如设备编号）。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `tag` | string | ✅ | 摄像头点位的 tag |
| `floor` | int | ⬜ | 摄像头所在楼层 |
| `app` | string | ⬜ | 应用标识，原样透传给前端 |
| `stream` | string | ⬜ | 视频流标识，原样透传给前端 |
| `name` | string | ⬜ | 视频窗标题，不传由前端决定 |
| `source_url` | string | ⬜ | 视频源地址，原样透传给前端 |

---

#### 8. `scene_show_inspection`

**描述：** 展示巡检：切到楼层、高亮巡检对象、打开巡检卡。用户说“看巡检”“巡检情况”“巡检路线”时用它。

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `floor` | int | ⬜ | 1 | 巡检所在楼层 |

---

#### 9. `scene_show_meeting_rooms`

**描述：** 展示会议室：切到楼层、高亮会议室分组、聚焦会议室相机。用户说“看会议室”“会议室在哪”“会议室情况”时用它。

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `floor` | int | ⬜ | 3 | 会议室所在楼层 |

---

#### 10. `scene_show_dining`

**描述：** 展示食堂：切到楼层、高亮食堂区域并聚焦。用户说“看食堂”“食堂在哪”“去食堂”时用它。

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `floor` | int | ⬜ | 1 | 食堂所在楼层 |

---

#### 11. `scene_show_area_overview`

**描述：** 展示防区总览：切到楼层、聚焦防区总览、高亮防区、逐个显示指定防区的监控点位。用户说“看防区”“防区总览”“哪些防区”时用它。

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `floor` | int | ⬜ | 11（整栋楼） | 防区所在楼层 |
| `tags` | string[] | ⬜ | — | 要高亮的防区标识列表 |

---

#### 12. `scene_show_device_category`

**描述：** 按设备类目显示点位：先隐藏已有的 POI，再切楼层，然后显示该楼层上这一类目下的所有点位。用户说“看 XX 设备”“显示所有摄像头/烟感/门禁”时用它。`category` 是设备类目名（如 `JIANKONG`）。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `category` | string | ✅ | 设备类目名，如 `JIANKONG` |
| `floor` | int | ⬜ | 限定楼层，不传由前端决定 |

---

#### 13. `scene_show_fusion`

**描述：** 展示虚实融合视频：切到楼层后把指定 `tag` 的实时视频融合到场景里。用户说“融合”“虚实融合”“把视频投到场景里”时用它。

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `tag` | string | ✅ | — | 融合目标的 tag |
| `build` | string | ⬜ | 室外 | 楼栋名 |
| `floorNum` | int | ⬜ | 13（室外） | 楼层 |
| `options` | string | ⬜ | — | 附加选项，原样透传给前端 |

---

### 5.3 原子动作

#### 14. `scene_focus_actor`

**描述：** 把相机聚焦到某个对象上，并让该对象显示出来。比组合动作更底层，只在没有合适组合动作时使用。用户说“聚焦到 XX”“拉近看 XX”“对准 XX”时用它。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `actorType` | string | ✅ | 对象大类，如 `BASEACTOR` / `POI` / `AREA` |
| `type` | string | ✅ | 对象子类，如 `JIANKONG` / `MEETING_ROOM` |
| `tag` | string | ✅ | 对象标识 |

> 前端会在 `FocusTo` 前自己补 `ViewActor`，**不要**再额外调
> `scene_set_actor_visible` 铺场。

---

#### 15. `scene_set_actor_visible`

**描述：** 显示或隐藏某个对象（不改视角）。需要精确控制某个对象的显隐时用它。注意：隐藏全部 POI 请用 `scene_set_poi_visible`。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `actorType` | string | ✅ | 对象大类，如 `BASEACTOR` / `POI` / `AREA` |
| `type` | string | ✅ | 对象子类，如 `JIANKONG` / `MEETING_ROOM` |
| `tag` | string | ✅ | 对象标识 |
| `visible` | bool | ✅ | `true`=显示（`ViewActor`），`false`=隐藏（`HideActor`） |

---

#### 16. `scene_set_poi_visible`

**描述：** 显示某一类 POI 点位，或隐藏全部 POI。`visible=false` 时忽略其他参数，直接隐藏全部 POI（前端走 `HidePOI`）。用户说“显示 XX 点位”“关掉点位”“清掉所有标记”时用它。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | string | ✅（schema） | POI 类目，`visible=true` 时必填 |
| `build` | string | ✅（schema） | 楼栋，`visible=true` 时必填 |
| `floorNum` | int | ✅（schema） | 楼层，`visible=true` 时必填 |
| `visible` | bool | ✅ | `true`=显示该类目，`false`=隐藏全部 POI |

> 注意：`visible=false` 时服务端**不会**下发 `type`/`build`/`floorNum`，
> 只发 `HidePOI`。但 schema 仍标必填，模型需要给这三个参数填值（可用占位值）。

---

#### 17. `scene_set_env`

**描述：** 调整场景的环境效果：时间或天气。两个参数至少要填一个，也可以同时填。用户说“天黑了”“改成傍晚”“下雨”“放晴”时用它。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `env_time` | string | ⬜ | 环境时间，如 `白天` / `傍晚` / `夜晚`，具体取值由前端定义 |
| `env_weather` | string | ⬜ | 环境天气，如 `晴` / `雨` / `雪`，具体取值由前端定义 |

> 运行时校验：两个都为空时直接返回
> `调用 scene_set_env 失败：env_time 和 env_weather 至少要填一个。`

---

### 5.4 破坏性动作（默认关闭）

#### 18. `scene_set_area_state`

**描述：** 设置防区状态（离线 / 布防 / 撤防 / 告警）。这是会改变现场状态的动作。用户说“把 XX 防区撤防”“给 XX 布防”时用它。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `tag` | string | ✅ | 防区标识 |
| `state` | enum | ✅ | `离线` \| `布防` \| `撤防` \| `告警` |

---

#### 19. `scene_set_inspection_state`

**描述：** 控制巡检任务（开始 / 暂停 / 结束）。这是会改变现场状态的动作。用户说“开始巡检”“暂停巡检”“结束巡检”时用它。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `tag` | string | ✅ | 巡检任务标识 |
| `state` | enum | ✅ | `开始` \| `暂停` \| `结束` |

> 以上两个动作受 `scene.destructive-enabled` 控制，**默认 `false`**。
> 关闭时调用会失败并返回“需要用户确认后才执行，当前阶段尚未开放”，不会下发。
> 等 Phase 4 接上确认卡片后再放行。

---

### 5.5 查询

#### 20. `get_scene_state`

**描述：** 查询场景页面当前的状态快照：是否就绪、当前在哪一层、哪栋楼、可选楼层列表。判断“用户现在看的是第几层”只能靠这个工具，不要靠你上一轮说过什么 —— 用户可能在页面里自己点了菜单切了楼层。无参数，不推送动作，只是读缓存。

**注意：** 工具名**没有 `scene_` 前缀**，且不在 `/scene/dispatch` 词表里，只能通过
MCP `tools/call` 调用。

返回示例：

```json
{ "type": "scene_state", "sessionId": "dev-1", "ready": true,
  "floor": 3, "build": "科研办公楼" }
```

异常返回：

| 情况 | 返回 |
|---|---|
| 没有已连接的页面 | `场景页面未连接，拿不到状态。请提示用户先打开场景页。` |
| 页面已连但尚未上报快照 | `场景页面已连接，但还没有上报过状态快照（可能正在加载）。` |

---

## 6. 返回值与错误语义

### 6.1 动作工具的成功返回

```text
已下发动作 scene_switch_floor（floor=3, focus=true）。
注意：这只代表动作帧已送到场景页面，UE 不回执，不代表场景已经改变。
回复用户时不要说"已完成"，只能说"已下发"。
```

**LLM 侧必须保留这个限定语**：`dispatched` ≠ 场景已改变。

### 6.2 动作工具的失败返回

```text
调用 scene_switch_floor（floor=3）失败：<error>（这个动作没有下发成功，不要告诉用户已经执行了）
```

常见 `error`：

| 值 | 含义 |
|---|---|
| 场景页面未连接 | `X-Session-Id` 没命中，或页面没连 |
| 缺少必填参数 xxx | 参数校验失败 |
| 未知动作 / 动作不在词表内 | 工具名与 `SceneActions` 不一致 |
| 该动作会改变现场状态…… | 破坏性动作业已关闭 |
| 动作执行超时（15s） | 帧可能已发出但回执丢失，**不代表没执行** |

### 6.3 MCP 协议层错误

- 中文枚举值写错会在协议层被拒，例如：
  `No enum constant com.hx.mcpsidecar.scene.SceneActions.AreaState.armed`
- 请求缺 `Content-Type: application/json` → `415 Unsupported Media Type`。
- 请求不存在的工具名 → JSON-RPC 错误。

---

## 7. 联调步骤

1. **前端页面连上** `/ws/scene`，发 `hello`，记下 `sessionId`（如 `dev-1`）。
2. **确认连接**：
   ```
   GET http://127.0.0.1:2222/scene/sessions
   ```
3. **不经 LLM 手测一个动作**：
   ```
   GET http://127.0.0.1:2222/scene/dispatch?action=scene_switch_floor&sessionId=dev-1&params=%7B%22floor%22%3A3%7D
   ```
   页面应切到 3 楼。
4. **MCP 握手 + tools/list**，确认 20 个工具都在。
5. **带 `X-Session-Id` 调 `tools/call`**：
   ```
   curl -X POST http://127.0.0.1:2222/mcp/scene \
     -H 'Content-Type: application/json' \
     -H 'Accept: application/json, text/event-stream' \
     -H 'Mcp-Session-Id: <initialize 返回的值>' \
     -H 'X-Session-Id: dev-1' \
     -d '{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"scene_switch_floor","arguments":{"floor":3}}}'
   ```
6. 验证 `get_scene_state` 能读到页面最新楼层。

---

## 8. 行为约定与限制

1. **不要重复铺场。** 前端执行器在每条 `FocusTo` 前都会自动补 `ViewActor`，
   想显示/聚焦某对象，调 `scene_focus_actor` 或组合动作即可；
   额外再调 `scene_set_actor_visible` 只是多发一条冗余帧。
2. **超时 15s。** 超时返回“执行状态未知”，不能当作“没执行”。
3. **重连重发。** 同一 `sessionId` 刷新页面重连时，在途动作会重发给新连接
   （`scene.redispatch-on-reconnect=true`）。非破坏性动作幂等，代价可接受。
4. **单会话兜底只适合联调。** 多用户必须逐请求注入 `X-Session-Id`。
5. **`sessionId` 来源尚未打通。** 方案只规定 agent 要注入，值目前由前端
   `hello` 先声明、agent 跟随。聊天入口需要把同一个值透传给 MCP 调用。
6. **无鉴权。** `/mcp/scene` 和 `/scene/*` 当前均无鉴权，生产部署需在网关层控制暴露面。
7. **动作词表是封闭集合。** sidecar 只下发 `SceneActions.ALL` 里的 19 个名字；
   表外动作会被拒绝。改词表要同时改 `SceneActions.java` 与 `SceneMcp.java`。

---

## 9. 相关文档

| 文档 | 内容 |
|---|---|
| [`场景动作下发接口.md`](./场景动作下发接口.md) | 请求格式速查、逐工具可复制样例、坑、前端 WS 协议细节 |
| [`../ai智能体架构.md`](../ai智能体架构.md) | agent 网关整体架构（非场景专项） |