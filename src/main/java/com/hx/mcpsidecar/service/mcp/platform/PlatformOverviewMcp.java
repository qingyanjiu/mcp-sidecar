package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.mock.MockData;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 综合态势mcp工具集 - 来自platform平台
 */
@Service
public class PlatformOverviewMcp {

    private static final String TOOL_NAME_PREFIX = "overview:";
    private static final String TOOL_DESC_PREFIX = "综合态势概览服务:";

    // 园区基本信息接口URL
    public static final String OVERVIEW_BASIC_INFO_URL = "/overview/basicInfo";
    // 设备类别健康度接口URL
    public static final String OVERVIEW_DEVICE_HEALTH_URL = "/overview/deviceHealth";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getBasicInfo",
            description = TOOL_DESC_PREFIX + """
            查询园区基本信息,返回:
            - centerArea: 园区面积
            - iotDeviceCount: IoT设备总数
            即大屏顶部园区概况卡的数据;
            请根据用户问题(如"园区面积多大"/"园区有多少IoT设备")从返回中提取对应信息回答。
            """
    )
    public String getBasicInfo() {
        String url = platformApiCallServiceImpl.getBaseUrl() + OVERVIEW_BASIC_INFO_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getDeviceHealth",
            description = TOOL_DESC_PREFIX + """
            查询各类别设备健康度,返回数组,每项包含:
            - name: 设备类别名称
            - score: 健康分
            - onlineRate: 在线率对象,含current(在线数)/total(总数)/percent(百分比)
            - maintenanceRate: 维修率对象,含percent(百分比)
            - lifeRate: 寿命率对象,含current(正常寿命数)/total(总数)/percent(百分比)
            即大屏设备健康度模块的数据;
            请根据用户问题(如"哪类设备健康度最低"/"设备在线率怎么样")从返回中提取对应信息回答。
            """
    )
    public String getDeviceHealth() {
        String url = platformApiCallServiceImpl.getBaseUrl() + OVERVIEW_DEVICE_HEALTH_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }
}
