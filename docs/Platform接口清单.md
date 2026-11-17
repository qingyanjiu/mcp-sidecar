# Platform 接口清单

以下为 sidecar 对接的平台接口，路径均相对于平台基础地址（默认 `http://<平台地址>:9500/platform`）。

## 登录与鉴权

| 接口 | 说明 |
|---|---|
| `GET /noLogin/getLoginInfo` | 获取平台登录加密配置；平台开启 RSA 登录时返回公钥。 |
| `POST /login` | 使用用户名、密码、验证码登录，返回访问 token。 |
| `GET /llmChat/auth` | 校验聊天 token 与 userId 是否匹配；返回 200 表示登录态有效。 |

## 综合态势

| 接口 | 说明 |
|---|---|
| `GET /overview/basicInfo` | 查询园区基本概况，包括园区面积、IoT 设备总数。 |
| `GET /overview/deviceHealth` | 查询各类设备的健康度，包括健康分、在线率、维修率、寿命率。 |

## 人员态势

| 接口 | 说明 |
|---|---|
| `GET /overview/getTodayPeople` | 查询今日人员态势，包括实时在园、今日进入、今日离开人数。 |
| `GET /personnel/personFlow` | 查询人员流动数据，按日、周、月返回进入与离开人数序列。 |
| `GET /personnel/personnelStructure` | 查询人员结构，按公司或单位统计人数。 |

## 车辆态势

| 接口 | 说明 |
|---|---|
| `GET /overview/peopleVehiclesNum` | 查询停车位统计。 |
| `GET /vehicle/trafficVolume` | 查询车流量统计，返回进出车流量趋势。 |
| `GET /vehicle/carStatistic` | 查询公车统计。 |
| `GET /vehiclePassRecord/parkingStructure` | 查询停车结构，返回停车类型和停车时长占比。 |
| `GET /vehiclePassRecord/parkingRank` | 查询停车时长排名。 |
| `GET /vehiclePassRecord/listByDeviceCode` | 按道闸设备编号查询车辆通行记录。 |

## 安全态势

| 接口 | 说明 |
|---|---|
| `GET /screenPage/anfang/securityIndex` | 查询园区综合安全指数。 |
| `POST /calldevice/alarm/anfang/alarmListWithType` | 查询安防告警列表，按安防告警、管理预警、环境预警分类。 |
| `POST /calldevice/alarm/anfang/alarmSituation` | 查询 AI 告警态势统计，返回各类别数量与占比。 |
| `POST /calldevice/alarm/anfang/getAlarmList` | 查询 AI 巡查事件列表。 |
| `POST /calldevice/alarm/anfang/getDeviceDetail` | 查询摄像头等安防设备的档案详情。 |
| `GET /deviceAlarm/aiAlarmView` | 查询 AI 告警总览统计。 |
| `GET /deviceAlarm/alertSituation` | 查询 AI 告警趋势，返回近 7 天和近 30 天的告警数量序列。 |
| `GET /screenPatrol/missionStat` | 查询近一周安全巡查任务统计。 |
| `GET /screenPatrol/inspectionTrend` | 查询安全巡查趋势，返回当天分时段的正常与异常数量。 |

## 周界

| 接口 | 说明 |
|---|---|
| `GET /perimeter/keyMetrics` | 查询周界防区关键指标。 |
| `GET /perimeter/areaOverview` | 查询周界防区一览。 |
| `GET /perimeter/perimeterAlarmStats` | 查询周界告警统计。 |
| `GET /perimeter/alarmOverview` | 查询周界告警一览。 |

## 巡检

| 接口 | 说明 |
|---|---|
| `GET /screenPatrol/todayOverview` | 查询今日巡检概览。 |
| `GET /screenPatrol/todayTaskList` | 查询今日巡检任务列表。 |
| `GET /screenPatrol/statistics` | 查询巡检统计。 |
| `GET /screenPatrol/executionStatus` | 查询巡检执行状态，按人员统计。 |

## 会议

| 接口 | 说明 |
|---|---|
| `GET /meeting/meetingStatistics` | 查询会议统计。 |
| `GET /meeting/highFrequencyMeetingRooms` | 查询高频会议室前 5 名。 |
| `GET /meeting/meetingRoomOverview` | 查询会议室一览及各会议室使用情况。 |
| `GET /meeting/numberOfMeetings` | 查询会议数量。 |
| `GET /meeting/meetingRoomStatus` | 查询会议室实时状态。 |
| `POST /meetBook/getRoomMeetListByDay` | 查询指定日期的会议安排。 |

## 食堂

| 接口 | 说明 |
|---|---|
| `POST /calldevice/dininghall/getWeeklyRecipe` | 查询当周每日食谱。 |
| `GET /canteen/getDishPopularity` | 查询菜品热度排行，返回热度最高的前 5 道菜。 |
| `GET /canteen/diningCount` | 查询就餐人数统计。 |

## 能源

| 接口 | 说明 |
|---|---|
| `GET /energy/overallEnergyConsu` | 查询总体能耗，包括年度和今日的用电、用水。 |
| `GET /energy/meteringEquipment` | 查询表具设备统计，包括电表、水表数量。 |
| `GET /energy/electricityUsageRanking` | 查询用电排名。 |
| `GET /energy/waterUsageRanking` | 查询用水排名。 |
| `GET /energy/deviceStatus` | 查询能耗设备的在线与离线数量。 |
| `GET /energy/realtimeElectricityUsage` | 查询本周与上周的用电对比。 |
| `GET /energy/realtimeWaterUsage` | 查询本周与上周的用水对比。 |

## 消防

| 接口 | 说明 |
|---|---|
| `POST /calldevice/fire/getAssetsList` | 查询消防设备台账。 |
| `POST /calldevice/fire/getAlarmNum` | 查询消防告警分类统计。 |
| `POST /calldevice/fire/getAlarmList` | 查询实时消防告警列表。 |
| `GET /fire/monthRepair` | 查询消防月度报修趋势。 |

## 信息发布

| 接口 | 说明 |
|---|---|
| `POST /calldevice/xxfb/deviceAndMaterialStatistic` | 查询信息发布设备与素材统计。 |
| `POST /calldevice/gb/deviceStatusStatistic` | 查询广播设备状态统计。 |
| `POST /calldevice/xxfb/getProgramCount` | 查询节目数量趋势。 |
| `POST /calldevice/gb/getDeviceMainInfoList` | 查询近七天的广播设备明细。 |
| `POST /information/taskTrend` | 查询信息发布任务执行趋势。 |

## 设备态势

| 接口 | 说明 |
|---|---|
| `GET /device/equipClass` | 查询设备分类数量占比。 |
| `GET /device/categoryHealth` | 查询设备类别健康度。 |
| `GET /device/monthMaintenance` | 查询月度维修量趋势。 |
| `GET /device/monthRepair` | 查询月度报修量趋势。 |
| `GET /monitor/statisRegion` | 查询各区域设备数量统计。 |
| `POST /screenPage/anfang/getAnfangDeviceOnlinePercentage` | 查询安防设备在线率。 |
| `POST /screenPage/anfang/getMjOnlinePercentage` | 查询门禁设备在线率。 |
| `POST /calldevice/gb/onlinePercentage` | 查询广播设备在线率。 |

## 设备查询

| 接口 | 说明 |
|---|---|
| `POST /deviceInfo/listOnly` | 查询设备资产清单，不分页，返回全部符合条件的设备。 |
| `POST /deviceInfo/selectListWithSpace` | 按空间节点查询其下所有下级设备，不分页。 |
| `GET /deviceInfo/getDetail` | 按设备内部 id 查询单台设备的详情。 |
| `POST /deviceInfo/queryDeviceStatistics` | 按单位、部门、设备类型统计设备数量，支持分页。 |
| `GET /deviceInfo/typeNumStatistic` | 按设备类型统计数量。 |
| `GET /deviceInfo/statusNumStatistic` | 按启用状态统计数量。 |
| `GET /deviceInfo/allDeviceStatusNumStatistic` | 按设备类型与启用状态交叉统计数量。 |
| `GET /deviceInfo/spaceNumStatistic` | 按空间位置统计设备数量。 |
| `GET /deviceInfo/floorNumStatistic` | 按楼层统计设备数量。**sidecar 已不再调用**：平台按 `space_id_path` 匹配，只统计到楼层下级房间，直接挂在楼层上的设备全部漏掉（实测总数 14，实际 578），`countByFloor` 改为复用 `spaceNumStatistic`。 |
| `GET /deviceInfo/monitorTypeStatisticInSpace` | 统计监控设备的各子类型数量。 |
| `GET /deviceInfo/monitorStatusStatisticInSpace` | 统计监控设备的各启用状态数量。 |
| `POST /deviceInfo/distribution` | 按单位、部门、设备类型统计设备分布及排名。 |
| `POST /deviceInfo/queryDeviceByParam` | 按参数分类统计设备数量，并给出启用与停用数。 |
| `GET /energy/onlineRate` | 查询能耗设备的在线率，基于平台独立的在线状态表。 |
| `GET /device/equipDistribution` | 查询各楼层的设备分布。 |
| `POST /screenPage/anfang/getCameraOnlinePercentage` | 查询监控设备的实时在线率，数据来自厂商平台。 |
| `GET /visual/getDeviceDetailByCode` | 按设备编号查询单台设备的详情。 |
| `POST /calldevice/jk/getDeviceList` | 查询监控设备列表，厂商平台实时透传。 |
| `POST /calldevice/mj/getDeviceList` | 查询门禁设备列表，厂商平台实时透传。 |
| `POST /calldevice/dz/getDeviceList` | 查询道闸设备列表，厂商平台实时透传。 |
| `POST /calldevice/gb/getDeviceList` | 查询广播设备列表，厂商平台实时透传。 |
| `POST /calldevice/xxfb/getDeviceList` | 查询信息发布设备列表，厂商平台实时透传。 |

## 路径规划

| 接口 | 说明 |
|---|---|
| `GET /visualPathFindGrid/listOnly` | 查询路径规划网格配置，返回空间内墙体矩阵。 |
