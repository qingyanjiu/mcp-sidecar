# 本周 MCP 服务开发计划表

> 生成日期：2026-08-25（周二）· 计划周期：**2026-08-24 ~ 2026-08-30**（本周）
> 依据：`D:\work\Company_project\webscode\tf_park_ue\docs\项目结构与接口文档.md`（2026-08-21 版）
> 源码位置：`src/main/java/com/hx/mcpsidecar/service/mcp/platform/`，工具命名前缀沿用现有 `模块:` 风格

---

## 一、现状盘点（mcp-sidecar 已有服务）

| MCP 服务文件 | 对应模块 | 已覆盖接口 | 状态 |
|---|---|---|---|
| [PlatformDiningHallMcp.java](../src/main/java/com/hx/mcpsidecar/service/mcp/platform/PlatformDiningHallMcp.java) | 食堂管理 | 菜谱 / 菜品热度 / 就餐人数 | ✅ 完整 |
| [PlatformVehicleStatusMcp.java](../src/main/java/com/hx/mcpsidecar/service/mcp/platform/PlatformVehicleStatusMcp.java) | 车辆态势 | parkingSpace / trafficVolume / parkingStructure / carStatistic / parkingRank | 🔶 缺 1 接口（改动未提交） |
| [PlatformPersonStatusMcp.java](../src/main/java/com/hx/mcpsidecar/service/mcp/platform/PlatformPersonStatusMcp.java) | 人员态势 | 今日态势 / 人员流动 / 人员结构 | 🔶 缺 4 接口 |
| [PlatformSecurityStatusMcp.java](../src/main/java/com/hx/mcpsidecar/service/mcp/platform/PlatformSecurityStatusMcp.java) | 安防态势 | alarmListWithType + securityIndex / alarmSituation / missionStat / inspectionTrend / getAlarmList / getDeviceDetail（7 工具，08-25 补齐） | ✅ 完整（未提交） |
| [PlatformPathFindMcp.java](../src/main/java/com/hx/mcpsidecar/service/mcp/platform/PlatformPathFindMcp.java) | 路径规划（基础） | pathFindGrid | ✅ |
| [PlatformInfoPublishMcp.java](../src/main/java/com/hx/mcpsidecar/service/mcp/platform/PlatformInfoPublishMcp.java) | 信息发布 | 一览/广播/任务趋势/节目数/设备明细（6 工具，08-25 完成） | ✅ |
| [PlatformEnergyMcp.java](../src/main/java/com/hx/mcpsidecar/service/mcp/platform/PlatformEnergyMcp.java) | 能源态势 | 总体能耗/表具/用电排名/用水排名/设备状态/实时电水（7 工具） | ✅ 未提交 |
| [PlatformMeetingMcp.java](../src/main/java/com/hx/mcpsidecar/service/mcp/platform/PlatformMeetingMcp.java) | 会议管理 | 会议统计/高频会议室/一览/数量/状态/当日会议（6 工具） | ✅ 未提交 |
| [TimeMcp.java](../src/main/java/com/hx/mcpsidecar/service/mcp/utils/TimeMcp.java) / WeatherMcp | 工具 | 时间 / 天气 | ✅ |

## 二、待开发模块清单（对照接口文档）

| 序号 | 模块 | 路由 | 接口数 | 优先级 |
|---|---|---|---|---|
| 1 | 综合态势-概览 overview | /compositive/overview | 6 | P0 |
| ~~2~~ | ~~能源态势 energy~~ | ~~/compositive/energy~~ | 7 | ✅ 已开发（未提交） |
| 3 | 事件态势 event | /compositive/event | 5 | P0 |
| 4 | 消防态势 fire | /emergency/fire | 4 | P0 |
| 5 | 孪生周界 perimeter | /emergency/perimeter | 4 | P0 |
| 6 | 孪生指挥 command | /emergency/command | 3 | P0 |
| 7 | AI 预警 aiAlert | /emergency/aiAlert | 3（1 复用安防） | P0 |
| ~~8~~ | ~~会议管理 meeting~~ | ~~/operation/meeting~~ | 6 | ✅ 已开发（未提交） |
| 9 | 孪生访客 twinVisitors | /operation/twinVisitors | 5 | P0 |
| 10 | 孪生巡检 inspection | /twins/inspection | 4 | P1 |
| 11 | 孪生监控 monitor | /twins/monitor | 2 | P1 |
| 12 | 轨迹回溯 trace | /twins/trace | 3 | P1 |
| 13 | 信息发布 information | /services/information | 5 | P1 |
| ~~14~~ | ~~设备态势 device~~ | ~~/services/device~~ | 8 | ✅ 已开发（未提交） |
| 15 | 网络管理 network | /services/network | 8 | P2 |
| 16 | 安全监控 security/monitor | /security/monitor（未挂路由） | 5 | P2 |

## 三、本周开发计划表

### 第 1 天：08-25（周二）—— 补齐已有服务缺口（小改动，先做）

| 目标文件 | 补齐接口 | 方法/URL | 说明 |
|---|---|---|---|
| PlatformVehicleStatusMcp | 道闸通行记录 | GET `/vehiclePassRecord/listByDeviceCode` | 弹窗类：车牌进出方向、通行时间 |
| PlatformPersonStatusMcp | 今日访客 | GET `/screenAppointment/todayVisitorCount` | 离开/预约/已到访/异常 |
| PlatformPersonStatusMcp | 来访时间段 | GET `/screenAppointment/visitingPeriod` | xData/yData 分布 |
| PlatformPersonStatusMcp | 被访部门 | GET `/screenAppointment/departmentByCompany` | 部门被访量占比 |
| PlatformPersonStatusMcp | 门禁通行记录 | GET `/accessRecord/listByDeviceCode` | 进出方向、开门成败 |
| PlatformSecurityStatusMcp | 综合安全指数 | GET `/screenPage/anfang/securityIndex` | 安全指数仪表盘（overview 复用） |
| PlatformSecurityStatusMcp | AI 告警态势 | POST `/calldevice/alarm/anfang/alarmSituation` | dahua/V5.0.16 |
| PlatformSecurityStatusMcp | 巡查任务 | GET `/screenPatrol/missionStat` | 近一周待处理/已处理/异常 |
| PlatformSecurityStatusMcp | 安全巡查趋势 | GET `/screenPatrol/inspectionTrend` | 正常/异常趋势折线 |
| PlatformSecurityStatusMcp | AI 巡查事件 | POST `/calldevice/alarm/anfang/getAlarmList` | dahua/V5.0.16 |
| PlatformSecurityStatusMcp | 设备详情 | POST `/calldevice/alarm/anfang/getDeviceDetail` | dahua/V5.0.16 |

### 第 2 天：08-26（周三）—— 综合态势概览 + 能源态势

| 目标文件 | 模块 | 接口（URL） | 优先级 |
|---|---|---|---|
| 新建 PlatformOverviewMcp.java | 综合态势概览 | GET `/overview/basicInfo`、`/overview/deviceHealth`、`/overview/peopleVehiclesNum`、`/overview/energyConsumption`、`/overview/todayMeet`、`/screenPage/anfang/securityIndex` | P0 |
| ~~新建 PlatformEnergyMcp.java~~ | 能源态势 | ~~GET `/energy/overallEnergyConsu` 等 7 接口~~ | ✅ 已开发（未提交） |

### 第 3 天：08-27（周四）—— 事件态势 + 消防态势

| 目标文件 | 模块 | 接口（URL） | 优先级 |
|---|---|---|---|
| 新建 PlatformEventMcp.java | 事件态势 | GET `/event/eventTicketList`、`/event/eventOverviewList`、`/event/workStatis`、`/event/workTrend`、`/event/workEfficiency` | P0 |
| 新建 PlatformFireMcp.java | 消防态势 | POST `/calldevice/fire/getAssetsList`、`/calldevice/fire/getAlarmNum`、`/calldevice/fire/getAlarmList`（均 sjy/V1.0，body 带 producerCode/protocolVersion）；GET `/fire/monthRepair` | P0 |

### 第 4 天：08-28（周五）—— 孪生周界 + 孪生指挥 + AI 预警

| 目标文件 | 模块 | 接口（URL） | 优先级 |
|---|---|---|---|
| 新建 PlatformPerimeterMcp.java | 孪生周界 | GET `/perimeter/keyMetrics`、`/perimeter/areaOverview`、`/perimeter/perimeterAlarmStats`、`/perimeter/alarmOverview` | P0 |
| 新建 PlatformCommandMcp.java | 孪生指挥 | GET `/command/emergencyLevel`、`/command/emergencyRs`、`/command/realTimeWarn`（3D 门禁/广播控制走 UE api_send，不做） | P0 |
| 新建 PlatformAiAlertMcp.java | AI 预警 | GET `/deviceAlarm/aiAlarmView`、`/aiAlert/classAlarmStatistic`、`/deviceAlarm/alertSituation`（告警明细复用安防的 alarmListWithType） | P0 |

### 第 5 天：08-29（周六）—— 会议管理 + 孪生访客

| 目标文件 | 模块 | 接口（URL） | 优先级 |
|---|---|---|---|
| ~~新建 PlatformMeetingMcp.java~~ | 会议管理 | ~~GET `/meeting/meetingStatistics` 等 6 接口~~ | ✅ 已开发（未提交） |
| 新建 PlatformVisitorMcp.java | 孪生访客 | GET `/screenAppointment/todayVisitorCount`、`/screenAppointment/visitingPeriod`、`/screenAppointment/visitorDepartTree`、`/screenAppointment/todayVisitorList`、`/twinVisitors/todayVisitor`、`/twinVisitors/targetDep` | P0 |

### 第 6 天：08-30（周日）—— 孪生应用三件套

| 目标文件 | 模块 | 接口（URL） | 优先级 |
|---|---|---|---|
| 新建 PlatformInspectionMcp.java | 孪生巡检 | GET `/screenPatrol/todayOverview`、`/screenPatrol/todayTaskList`、`/screenPatrol/statistics`、`/screenPatrol/executionStatus` | P1 |
| 新建 PlatformMonitorMcp.java | 孪生监控 | GET `/monitor/equipType`、`/monitor/statisRegion?deviceType=x` | P1 |
| 新建 PlatformTraceMcp.java | 轨迹回溯 | POST `/visual/anfang/ai/snapShotFaceSearch`（dahua）、POST `/user/listUserOnlyRightLike`、GET `/user/selectById` | P1 |

### 溢出项（本周若有余力则做，否则下周一优先）

| 目标文件 | 模块 | 接口（URL） | 优先级 |
|---|---|---|---|
| ~~新建 PlatformInfoPublishMcp.java~~ | 信息发布 | ~~POST `/calldevice/xxfb/deviceAndMaterialStatistic`(hx)、`/calldevice/gb/deviceStatusStatistic`(itc)、`/information/taskTrend`、`/calldevice/xxfb/getProgramCount`、`/calldevice/gb/getDeviceMainInfoList`(itc)~~ | ✅ 已提前至 08-25 完成 |
| ~~新建 PlatformDeviceMcp.java~~ | 设备态势 | ~~GET `/device/equipClass`、`/device/categoryHealth`、`/device/monthMaintenance`、`/device/monthRepair`、`/monitor/statisRegion`；POST `/screenPage/anfang/getAnfangDeviceOnlinePercentage`、`/calldevice/gb/onlinePercentage`、`/screenPage/anfang/getMjOnlinePercentage`~~ | ✅ 已开发（未提交，09-10 完成） |
| 新建 PlatformNetworkMcp.java | 网络管理 | POST `/calldevice/network/getDeviceMonitorList`、`/getApList`、`/getBasicInfo`、`/getAlarmList`、`/getSwitchFlow`、`/getWirelessAccess`、`/getOnlineBehavior`（均 sjy/V1.0）；GET `/network/trafficTrend` | P2 |
| 新建 PlatformSecurityMonitorMcp.java | 安全监控（未挂路由） | GET `/security/monitor/overview`、`/deviceOnlineRate`、`/alarmTrend`；POST `/security/monitor/alarm/list`、`/device/list` | P2 |

---

## 四、开发规范（沿用现有代码风格）

1. **文件位置**：`service/mcp/platform/PlatformXxxMcp.java`，类加 `@Service` 注解与 `平台xxx服务` 类注释。
2. **前缀常量**：`TOOL_NAME_PREFIX`（如 `overview:`）、`TOOL_DESC_PREFIX`（如 `综合态势概览服务:`）。
3. **URL 常量**：每个接口一个 `public static final String ..._URL`，拼 `getBaseUrl()` 调用。
4. **调用方式**：GET 用 `platformApiCallServiceImpl.doGetCall(url)`；POST 用 `doPostCall(url, body)`，第三方协议接口（sjy/itc/hx/dahua/zhiye）需在 body 传 `producerCode` + `protocolVersion`（参考 PlatformDiningHallMcp）。
5. **返回整形**：大而复杂的返回（如周菜谱）参照 PlatformDiningHallMcp 的 `shapeXxx()` 思路压成紧凑结构，减少传给 LLM 的 token。
6. **注释质量**：每个 `@McpTool` 的 description 写明返回字段含义，并给"用户怎么问 → 怎么答"的指引。
7. **验证方式**：每完成一个模块，通过 MCP 客户端（Claude Desktop / IDE 调试）实际调用工具，确认能连上平台并返回真实数据。

## 五、说明与风险

- **P0 模块本周必须完成**（8/25~8/28），P1（8/29~8/30），P2 尽量做、做不完排下周一。
- 车辆态势当前有未提交改动（parkingSpace 等接口调整），补缺口前先确认该改动已收尾。
- `/security/monitor` 页面在 UE 端未挂路由，MCP 侧优先级最低，可在下周补。
- 静态视频（`jk.json`）与 3D 场景控制（UE api_send）不属于 HTTP 接口，MCP 不实现。
