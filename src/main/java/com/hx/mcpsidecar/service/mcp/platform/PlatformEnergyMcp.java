package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.mock.MockData;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 能源态势mcp工具集 - 来自platform平台
 */
@Service
public class PlatformEnergyMcp {

    private static final String TOOL_NAME_PREFIX = "energy:";
    private static final String TOOL_DESC_PREFIX = "能源态势服务:";

    // 总体能耗接口URL
    public static final String OVERALL_ENERGY_CONSU_URL = "/energy/overallEnergyConsu";
    // 表具设备接口URL
    public static final String METERING_EQUIPMENT_URL = "/energy/meteringEquipment";
    // 用电排名接口URL
    public static final String ELECTRICITY_USAGE_RANKING_URL = "/energy/electricityUsageRanking";
    // 用水排名接口URL
    public static final String WATER_USAGE_RANKING_URL = "/energy/waterUsageRanking";
    // 设备状态接口URL
    public static final String DEVICE_STATUS_URL = "/energy/deviceStatus";
    // 实时用电接口URL
    public static final String REALTIME_ELECTRICITY_USAGE_URL = "/energy/realtimeElectricityUsage";
    // 实时用水接口URL
    public static final String REALTIME_WATER_USAGE_URL = "/energy/realtimeWaterUsage";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getOverallEnergyConsu",
            description = TOOL_DESC_PREFIX + """
            查询总体能耗,可查到年度耗电、年度耗水、今日用电和今日用水,返回:
            - electricity: {annual, today} 年度耗电与今日用电
            - water: {annual, today} 年度耗水与今日用水
            请根据用户问题(如"今天用电多少"/"年度耗电"/"今日用水"/"年度耗水")从返回中提取对应信息回答。
            """
    )
    public String getOverallEnergyConsu() {
        String url = platformApiCallServiceImpl.getBaseUrl() + OVERALL_ENERGY_CONSU_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getMeteringEquipment",
            description = TOOL_DESC_PREFIX + """
            查询表具设备,返回:
            - electricityMeterCount: 电表数量
            - waterMeterCount: 水表数量
            请根据用户问题(如"有多少电表"/"表具设备")从返回中提取对应信息回答。
            """
    )
    public String getMeteringEquipment() {
        String url = platformApiCallServiceImpl.getBaseUrl() + METERING_EQUIPMENT_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getElectricityUsageRanking",
            description = TOOL_DESC_PREFIX + """
            查询用电排名,返回:
            - {name, value}[]: 用电单位/区域排名数组
            请根据用户问题(如"哪个部门用电最多"/"用电排名")从返回中提取对应信息回答。
            """
    )
    public String getElectricityUsageRanking() {
        String url = platformApiCallServiceImpl.getBaseUrl() + ELECTRICITY_USAGE_RANKING_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getWaterUsageRanking",
            description = TOOL_DESC_PREFIX + """
            查询用水排名,返回:
            - {name, value}[]: 用水单位/区域排名数组
            请根据用户问题(如"哪个部门用水最多"/"用水排名")从返回中提取对应信息回答。
            """
    )
    public String getWaterUsageRanking() {
        String url = platformApiCallServiceImpl.getBaseUrl() + WATER_USAGE_RANKING_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getDeviceStatus",
            description = TOOL_DESC_PREFIX + """
            查询能耗设备状态,返回:
            - onlineCount: 在线设备数量
            - offlineCount: 离线设备数量
            请根据用户问题(如"能耗设备在线情况"/"设备状态")从返回中提取对应信息回答。
            """
    )
    public String getDeviceStatus() {
        String url = platformApiCallServiceImpl.getBaseUrl() + DEVICE_STATUS_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getRealtimeElectricityUsage",
            description = TOOL_DESC_PREFIX + """
            查询用电对比,返回上周vs本周用电对比列表,每项含:
            - timeLabel: 时间标签
            - todayValue: 本周用电值
            - yesterdayValue: 上周用电值
            请根据用户问题(如"本周用电情况"/"上周和本周用电对比")从返回中提取对应信息回答。
            """
    )
    public String getRealtimeElectricityUsage() {
        String url = platformApiCallServiceImpl.getBaseUrl() + REALTIME_ELECTRICITY_USAGE_URL + "?statType=day";
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getRealtimeWaterUsage",
            description = TOOL_DESC_PREFIX + """
            查询用水对比,返回上周vs本周用水对比列表,每项含:
            - timeLabel: 时间标签
            - todayValue: 本周用水值
            - yesterdayValue: 上周用水值
            请根据用户问题(如"本周用水情况"/"上周和本周用水对比")从返回中提取对应信息回答。
            """
    )
    public String getRealtimeWaterUsage() {
        String url = platformApiCallServiceImpl.getBaseUrl() + REALTIME_WATER_USAGE_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }
}
