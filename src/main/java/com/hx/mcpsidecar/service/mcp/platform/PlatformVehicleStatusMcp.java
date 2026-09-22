package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.mock.MockData;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 车辆态势mcp工具集 - 来自platform平台
 */
@Service
public class PlatformVehicleStatusMcp {

    private static final String TOOL_NAME_PREFIX = "vehicle_status:";
    private static final String TOOL_DESC_PREFIX = "车辆态势服务:";

    // 停车位统计接口URL(页面停车位卡片实际走人车数量接口,取车位字段)
    public static final String PEOPLE_VEHICLES_NUM_URL = "/overview/peopleVehiclesNum";
    // 车流量统计接口URL
    public static final String TRAFFIC_VOLUME_URL = "/vehicle/trafficVolume";
    // 停车结构接口URL
    public static final String PARKING_STRUCTURE_URL = "/vehiclePassRecord/parkingStructure";
    // 公车统计接口URL
    public static final String CAR_STATISTIC_URL = "/vehicle/carStatistic";
    // 停车时长排名接口URL
    public static final String PARKING_RANK_URL = "/vehiclePassRecord/parkingRank";
    // 道闸通行记录接口URL
    public static final String VEHICLE_PASS_RECORD_URL = "/vehiclePassRecord/listByDeviceCode";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getParkingSpace",
            description = TOOL_DESC_PREFIX + """
            查询停车位统计,返回:
            - parkingTotal: 车位总数
            - parkingUsed: 已用车位数
            - parkingUsedRate: 已用率(如 "60%")
            - parkingFree: 空闲车位数
            - parkingFreeRate: 空闲率(如 "40%")
            - currentPeopleCount: 实时在园人数
            请根据用户问题(如"停车场还剩多少空位"/"车位占用率"/"总共有多少车位")从返回中提取对应信息回答。
            """
    )
    public String getParkingSpace() {
        String url = platformApiCallServiceImpl.getBaseUrl() + PEOPLE_VEHICLES_NUM_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getTrafficVolume",
            description = TOOL_DESC_PREFIX + """
            查询车流量统计,返回进出车流量趋势:
            - category: X轴时间段分类数组
            - oneData: 进车流量数组
            - twoData: 出车流量数组
            请根据用户问题(如"今天车流量如何"/"哪个时段车流高峰")从返回中提取对应信息回答。
            """
    )
    public String getTrafficVolume() {
        String url = platformApiCallServiceImpl.getBaseUrl() + TRAFFIC_VOLUME_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getParkingStructure",
            description = TOOL_DESC_PREFIX + """
            查询停车结构,返回停车类型/时长结构占比数组,每项含:
            - value: 数量或占比
            - name: 类型名称
            - itemStyle: 图表样式(如 color)
            请根据用户问题(如"停车结构分布")从返回中提取对应信息回答。
            """
    )
    public String getParkingStructure() {
        String url = platformApiCallServiceImpl.getBaseUrl() + PARKING_STRUCTURE_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getCarStatistic",
            description = TOOL_DESC_PREFIX + """
            查询公车统计,返回:
            - total: 公车总数
            - used: 使用中数量
            - inCompany: 在司数量
            - outCompany: 外出数量
            请根据用户问题(如"公车总数多少"/"公车使用情况")从返回中提取对应信息回答。
            """
    )
    public String getCarStatistic() {
        String url = platformApiCallServiceImpl.getBaseUrl() + CAR_STATISTIC_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getParkingRank",
            description = TOOL_DESC_PREFIX + """
            查询停车时长排名,返回:
            - overtimeStayRate: 超时停留率
            - avgParkingHours / avgParkingMinutes: 平均停车时长(小时/分钟)
            - tableData: 车牌停车时长排名表,每项含 carId(车牌号)/parkTime(停车时长)
            请根据用户问题(如"哪些车停得最久"/"停车时长排名")从返回中提取对应信息回答。
            """
    )
    public String getParkingRank() {
        String url = platformApiCallServiceImpl.getBaseUrl() + PARKING_RANK_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getVehiclePassRecord",
            description = TOOL_DESC_PREFIX + """
            查询道闸通行记录(页面点击3D道闸点位弹窗展示),返回车辆进出记录数组,每项含:
            - carNumber: 车牌号
            - passTime: 通行时间
            - eventType: 进出方向,"1"=进,"2"=出
            - deviceName: 道闸名称
            - deviceCode: 道闸编码
            - recordId: 记录ID
            code 为道闸设备编码(来自3D点位);若无法提供可留空,由后端返回默认记录。
            请根据用户问题(如"某道闸最近的车辆进出记录")从返回中提取对应信息回答。
            """
    )
    public String getVehiclePassRecord(String code) {
        String url = platformApiCallServiceImpl.getBaseUrl() + VEHICLE_PASS_RECORD_URL;
        if (code != null && !code.trim().isEmpty()) {
            url = url + "?code=" + code;
        }
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }
}
