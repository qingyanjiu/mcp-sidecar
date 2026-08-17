package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
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

    // 停车位统计接口URL
    public static final String PEOPLE_VEHICLES_NUM_URL = "/overview/peopleVehiclesNum";
    // 车流量统计接口URL
    public static final String TRAFFIC_VOLUME_URL = "/vehicle/trafficVolume";
    // 停车结构接口URL
    public static final String PARKING_STRUCTURE_URL = "/vehiclePassRecord/parkingStructure";
    // 公车统计接口URL
    public static final String CAR_STATISTIC_URL = "/vehicle/carStatistic";
    // 停车时长排名接口URL
    public static final String PARKING_RANK_URL = "/vehiclePassRecord/parkingRank";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @McpTool(
            name = TOOL_NAME_PREFIX + "getParkingNum",
            description = TOOL_DESC_PREFIX + """
            查询停车位统计,返回实时在园车辆/人员数量等信息,
            请根据用户问题(如"现在停了多少辆车"/"停车场余位")从返回中提取对应信息回答。
            """
    )
    public String getParkingNum() {
        String url = platformApiCallServiceImpl.getBaseUrl() + PEOPLE_VEHICLES_NUM_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getTrafficVolume",
            description = TOOL_DESC_PREFIX + """
            查询车流量统计,返回各时段/出入口的车流量数据,
            请根据用户问题(如"今天车流量如何"/"车流高峰")从返回中提取对应信息回答。
            """
    )
    public String getTrafficVolume() {
        String url = platformApiCallServiceImpl.getBaseUrl() + TRAFFIC_VOLUME_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getParkingStructure",
            description = TOOL_DESC_PREFIX + """
            查询停车结构,返回停车场/车型/区域等维度的结构分布数据,
            请根据用户问题(如"停车结构分布")从返回中提取对应信息回答。
            """
    )
    public String getParkingStructure() {
        String url = platformApiCallServiceImpl.getBaseUrl() + PARKING_STRUCTURE_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getCarStatistic",
            description = TOOL_DESC_PREFIX + """
            查询公车统计,返回公务车辆的数量/使用等统计数据,
            请根据用户问题(如"公车数量"/"公车使用情况")从返回中提取对应信息回答。
            """
    )
    public String getCarStatistic() {
        String url = platformApiCallServiceImpl.getBaseUrl() + CAR_STATISTIC_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getParkingRank",
            description = TOOL_DESC_PREFIX + """
            查询停车时长排名,返回车辆停车时长排行数据,
            请根据用户问题(如"哪些车停得最久"/"停车时长排名")从返回中提取对应信息回答。
            """
    )
    public String getParkingRank() {
        String url = platformApiCallServiceImpl.getBaseUrl() + PARKING_RANK_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }
}
