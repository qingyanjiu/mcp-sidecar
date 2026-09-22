package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.mock.MockData;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 信息发布mcp工具集 - 来自platform平台
 * 工具名与 ai-agentic-app(graph/information_status_langgraph.py) 的 _JAVA_TOOL_MAP 对齐:
 *   information:getInfoView / getBroadcastView / getTaskTrend / getProgramCount / getInfoPublishiEquip / getBroadcastEquip
 * 前端 langgraph 调用时统一传 startTime/endTime 两个参数,各工具按需使用,不需要时间的工具忽略即可。
 */
@Service
public class PlatformInfoPublishMcp {

    private static final String TOOL_NAME_PREFIX = "information:";
    private static final String TOOL_DESC_PREFIX = "信息发布服务:";

    // 信息发布一览/信息发布设备共用接口(信息发布方 hx/V4.8.5)
    public static final String INFO_STATISTIC_URL = "/calldevice/xxfb/deviceAndMaterialStatistic";
    // 广播一览接口(广播方 itc/V1.0)
    public static final String BROADCAST_STATISTIC_URL = "/calldevice/gb/deviceStatusStatistic";
    // 任务执行趋势接口
    public static final String TASK_TREND_URL = "/information/taskTrend";
    // 节目数量趋势接口(信息发布方 hx/V4.8.5)
    public static final String PROGRAM_COUNT_URL = "/calldevice/xxfb/getProgramCount";
    // 广播设备接口(广播方 itc/V1.0)
    public static final String BROADCAST_DEVICE_URL = "/calldevice/gb/getDeviceMainInfoList";

    // 第三方协议必传参数:信息发布方(生产者编码 hx / 协议版本 V4.8.5)
    public static final String INFO_PRODUCER_CODE = "hx";
    public static final String INFO_PROTOCOL_VERSION = "V4.8.5";
    // 第三方协议必传参数:广播方(生产者编码 itc / 协议版本 V1.0)
    public static final String BROADCAST_PRODUCER_CODE = "itc";
    public static final String BROADCAST_PROTOCOL_VERSION = "V1.0";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getInfoView",
            description = TOOL_DESC_PREFIX + """
            查询信息发布一览,返回:
            - statisticList: 发布终端统计,每项含 value(数量)/name(状态,如在线/离线/占用)
            - deviceList: 信息发布设备列表,每项含 name(设备名)/status(状态)/online(是否在线)
            - typeData: 发布素材类型占比,每项含 name(类型)/value(数量)
            请根据用户问题(如"信息发布终端统计"/"发布设备状态")从返回中提取对应信息回答。
            """
    )
    public String getInfoView(
            @McpToolParam(description = "查询开始时间,格式 yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式 yyyy-MM-dd,可不传") String endTime
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + INFO_STATISTIC_URL;
        Object result = platformApiCallServiceImpl.doPostCall(url, buildInfoBody());
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getBroadcastView",
            description = TOOL_DESC_PREFIX + """
            查询广播一览,返回广播设备状态统计数组,每项含:
            - name: 状态(如 总计/在线/离线/占用)
            - value: 对应数量
            请根据用户问题(如"广播设备在线情况"/"广播状态统计")从返回中提取对应信息回答。
            """
    )
    public String getBroadcastView(
            @McpToolParam(description = "查询开始时间,格式 yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式 yyyy-MM-dd,可不传") String endTime
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + BROADCAST_STATISTIC_URL;
        Object result = platformApiCallServiceImpl.doPostCall(url, buildBroadcastBody());
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getTaskTrend",
            description = TOOL_DESC_PREFIX + """
            查询发布任务执行趋势,返回:
            - xData: 时间轴分类数组(按天为小时/按周为星期/按月为日期)
            - data.oneData / data.twoData: 两组任务执行量数组(通常为不同任务类型的执行量)
            请根据用户问题(如"发布任务执行趋势"/"任务执行量变化")从返回中提取对应信息回答。
            """
    )
    public String getTaskTrend(
            @McpToolParam(description = "查询开始时间,格式 yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式 yyyy-MM-dd,可不传") String endTime
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + TASK_TREND_URL;
        // 平台按 day/week/month 返回不同粒度的趋势,默认 day;带时间范围则把起止时间一并传给平台
        Map<String, Object> body = new HashMap<>();
        body.put("type", "day");
        if (startTime != null && !startTime.isEmpty()) {
            body.put("startTime", startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            body.put("endTime", endTime);
        }
        Object result = platformApiCallServiceImpl.doPostCall(url, body);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getProgramCount",
            description = TOOL_DESC_PREFIX + """
            查询节目数量趋势,返回:
            - xData: 时间轴分类数组
            - count: 与 xData 对应的节目数量数组
            请根据用户问题(如"节目数量趋势"/"发布了多少节目")从返回中提取对应信息回答。
            """
    )
    public String getProgramCount(
            @McpToolParam(description = "查询开始时间,格式 yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式 yyyy-MM-dd,可不传") String endTime
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + PROGRAM_COUNT_URL;
        Object result = platformApiCallServiceImpl.doPostCall(url, buildInfoBody());
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getInfoPublishiEquip",
            description = TOOL_DESC_PREFIX + """
            查询信息发布设备明细,返回:
            - statisticList: 发布终端统计,每项含 value(数量)/name(状态)
            - deviceList: 信息发布设备列表,每项含 name(设备名)/status(状态)/online(是否在线)
            - typeData: 发布素材类型占比,每项含 name/value
            请根据用户问题(如"有哪些信息发布设备"/"信息发布设备明细")从返回中提取对应信息回答。
            """
    )
    public String getInfoPublishiEquip(
            @McpToolParam(description = "查询开始时间,格式 yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式 yyyy-MM-dd,可不传") String endTime
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + INFO_STATISTIC_URL;
        Object result = platformApiCallServiceImpl.doPostCall(url, buildInfoBody());
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getBroadcastEquip",
            description = TOOL_DESC_PREFIX + """
            查询广播设备明细(近七天),返回数组,每项含:
            - name: 广播设备标识(如 IP 地址)
            - status: 设备状态(在线/离线)
            - taskName: 当前执行的任务名(可能为空)
            请根据用户问题(如"广播设备有哪些"/"广播设备明细")从返回中提取对应信息回答。
            """
    )
    public String getBroadcastEquip(
            @McpToolParam(description = "查询开始时间,格式 yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式 yyyy-MM-dd,可不传") String endTime
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + BROADCAST_DEVICE_URL;
        Object result = platformApiCallServiceImpl.doPostCall(url, buildBroadcastBody());
        return JSON.toJSONString(result);
    }

    /**
     * 信息发布方(hx/V4.8.5)请求体:producerCode + protocolVersion 放 body,不能拼 query
     */
    private static Map<String, Object> buildInfoBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", INFO_PRODUCER_CODE);
        body.put("protocolVersion", INFO_PROTOCOL_VERSION);
        return body;
    }

    /**
     * 广播方(itc/V1.0)请求体:producerCode + protocolVersion 放 body,不能拼 query
     */
    private static Map<String, Object> buildBroadcastBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", BROADCAST_PRODUCER_CODE);
        body.put("protocolVersion", BROADCAST_PROTOCOL_VERSION);
        return body;
    }
}
