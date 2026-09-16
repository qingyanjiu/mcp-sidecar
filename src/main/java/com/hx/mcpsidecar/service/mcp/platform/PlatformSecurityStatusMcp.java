package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 安防态势mcp工具集 - 来自platform平台
 */
@Service
public class PlatformSecurityStatusMcp {

    private static final String TOOL_NAME_PREFIX = "security:";
    private static final String TOOL_DESC_PREFIX = "安防态势服务:";

    // 查询安防告警列表接口URL
    public static final String SECURITY_ALARM_LIST_URL = "/calldevice/alarm/anfang/alarmListWithType";

    // 查询综合安全指数接口URL
    public static final String SECURITY_INDEX_URL = "/screenPage/anfang/securityIndex";
    // 查询AI告警态势接口URL
    public static final String AI_ALARM_SITUATION_URL = "/calldevice/alarm/anfang/alarmSituation";
    // 查询AI告警总览接口URL
    public static final String AI_ALARM_VIEW_URL = "/deviceAlarm/aiAlarmView";
    // 查询AI告警趋势接口URL
    public static final String ALERT_SITUATION_URL = "/deviceAlarm/alertSituation";
    // 查询巡查任务统计接口URL
    public static final String PATROL_MISSION_URL = "/screenPatrol/missionStat";
    // 查询安全巡查趋势接口URL
    public static final String INSPECTION_TREND_URL = "/screenPatrol/inspectionTrend";
    // 查询AI巡查事件接口URL
    public static final String AI_INSPECTION_EVENTS_URL = "/calldevice/alarm/anfang/getAlarmList";
    // 查询设备详情接口URL
    public static final String DEVICE_DETAIL_URL = "/calldevice/alarm/anfang/getDeviceDetail";
    // 大华协议接口必传参数:调用方标识(生产者编码)
    public static final String DAHUA_PRODUCER_CODE = "dahua";
    // 大华协议接口必传参数:协议版本号
    public static final String DAHUA_PROTOCOL_VERSION = "V5.0.16";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @McpTool(
            name = TOOL_NAME_PREFIX + "getSecurityAlarmList",
            description = TOOL_DESC_PREFIX + """
            查询安防告警列表(分类告警明细),返回按类别分组的告警列表,key为类别名(安防告警/管理预警/环境预警),每个告警项包含:
            - alarmTypeName: 告警名称
            - alarmDate: 告警时间
            - alarmPosition: 告警位置
            - handleStat: 处理状态(0未处理/1已处理)
            - alarmGrade: 告警等级(1一般/2重要,更大更严重)
            请根据用户问题(如"安防告警有哪些"/"最近有什么告警")从返回中提取对应类别与信息回答。
            """
    )
    public String getSecurityAlarmList() {
        String url = platformApiCallServiceImpl.getBaseUrl() + SECURITY_ALARM_LIST_URL;
        Object result = platformApiCallServiceImpl.doPostCall(url, buildAlarmListWithTypeBody(null, null));
        return JSON.toJSONString(result);
    }

    // 构造分类告警明细接口(alarmListWithType)的POST请求体:大华协议必传producerCode/protocolVersion,过滤条件与大屏一致
    private Map<String, Object> buildAlarmListWithTypeBody(String startTime, String endTime) {
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", DAHUA_PRODUCER_CODE);
        body.put("protocolVersion", DAHUA_PROTOCOL_VERSION);
        body.put("deviceCategory", 1);
        body.put("alarmTypeList", Arrays.asList(303, 307, 311, 312, 596, 597));
        body.put("alarmGradeList", Arrays.asList(1, 2));
        body.put("filterAi", true);
        if (startTime != null && !startTime.isEmpty()) {
            body.put("startTime", startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            body.put("endTime", endTime);
        }
        return body;
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getSecurityIndex",
            description = TOOL_DESC_PREFIX + """
            查询园区综合安全指数,返回值包含:
            - index: 综合安全指数分数
            - indicators: 各分项指标数组,每项包含 name(指标名)/value(数值)/unit(单位)
            请根据用户问题(如"园区安全指数多少"/"安全状况怎么样")从返回中提取对应信息回答。
            """
    )
    public String getSecurityIndex() {
        String url = platformApiCallServiceImpl.getBaseUrl() + SECURITY_INDEX_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getAiAlertSituation",
            description = TOOL_DESC_PREFIX + """
            查询AI告警态势统计,返回各AI告警类别的数量和占比,Record的key为告警类别名,value包含:
            - count: 该类告警数量
            - percentage: 占比百分比
            startTime/endTime可选,格式yyyy-MM-dd,不传默认查当天;用户问"AI告警分布"/"各类智能告警占比"时使用。
            """
    )
    public String getAiAlertSituation(
            @McpToolParam(description = "查询开始时间,格式yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式yyyy-MM-dd,可不传") String endTime
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + AI_ALARM_SITUATION_URL;
        // 该接口是POST方式,producerCode/protocolVersion要放请求体里;过滤条件与大屏一致
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", DAHUA_PRODUCER_CODE);
        body.put("protocolVersion", DAHUA_PROTOCOL_VERSION);
        body.put("deviceCategory", 1);
        body.put("alarmTypeList", Arrays.asList(303, 307, 311, 312, 596, 597));
        body.put("alarmGradeList", Arrays.asList(1, 2));
        body.put("filterAi", true);
        if (startTime != null && !startTime.isEmpty()) {
            body.put("startTime", startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            body.put("endTime", endTime);
        }
        Object result = platformApiCallServiceImpl.doPostCall(url, body);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getPatrolMission",
            description = TOOL_DESC_PREFIX + """
            查询近一周安全巡查任务统计,返回数组,每项包含:
            - name: 任务状态名(待处理/已处理/异常)
            - value: 对应数量
            请根据用户问题(如"巡查任务完成情况"/"本周巡查待处理多少")从返回中提取对应信息回答。
            """
    )
    public String getPatrolMission() {
        String url = platformApiCallServiceImpl.getBaseUrl() + PATROL_MISSION_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getInspectionTrend",
            description = TOOL_DESC_PREFIX + """
            查询安全巡查趋势(当天分时段正常/异常),返回值包含:
            - category: X轴时间分类
            - oneData: 正常巡查数量序列
            - twoData: 异常巡查数量序列
            date参数可选,格式yyyy-MM-dd,不传默认当天;
            请根据用户问题(如"今天巡查情况"/"巡查异常多不多")从返回中提取对应信息回答。
            """
    )
    public String getInspectionTrend(
            @McpToolParam(description = "查询日期,格式yyyy-MM-dd,可不传默认当天") String date
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + INSPECTION_TREND_URL;
        if (date != null && !date.isEmpty()) {
            url += "?date=" + date;
        }
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getAiInspectionEvents",
            description = TOOL_DESC_PREFIX + """
            查询AI巡查事件列表,返回数组,每项包含:
            - alarmTypeName: 告警类型名
            - alarmGrade: 告警等级(1一般/2重要,更大更严重)
            - alarmDate: 告警时间
            - alarmPosition: 告警位置
            - orgName: 所属组织
            - alarmPicture/pic: 抓拍图片地址
            - handleStat: 处理状态
            startTime/endTime可选,格式yyyy-MM-dd;
            请根据用户问题(如"AI巡查发现了什么异常"/"最近智能巡检告警")从返回中提取对应信息回答。
            """
    )
    public String getAiInspectionEvents(
            @McpToolParam(description = "查询开始时间,格式yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式yyyy-MM-dd,可不传") String endTime
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + AI_INSPECTION_EVENTS_URL;
        // 该接口是POST方式,producerCode/protocolVersion要放请求体里;过滤条件与大屏一致
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", DAHUA_PRODUCER_CODE);
        body.put("protocolVersion", DAHUA_PROTOCOL_VERSION);
        body.put("deviceCategory", 1);
        body.put("alarmGradeList", Arrays.asList(1, 2));
        if (startTime != null && !startTime.isEmpty()) {
            body.put("startTime", startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            body.put("endTime", endTime);
        }
        Object result = platformApiCallServiceImpl.doPostCall(url, body);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getDeviceDetail",
            description = TOOL_DESC_PREFIX + """
            查询摄像头/安防设备档案详情,返回数组,每项包含:
            - channelName: 通道/设备名称
            - channelCode: 通道编码
            - deviceCode: 设备编码
            - channelDeviceIp: 设备IP
            - isOnline: 是否在线(1在线/0离线)
            - cameraType: 摄像头类型
            - gpsX/gpsY: 经纬度
            channelCodeList为可选的通道编码列表(逗号分隔),传入则只查这些通道,不传查全部;
            请根据用户问题(如"某摄像头在不在线"/"监控设备详情")从返回中提取对应信息回答。
            """
    )
    public String getDeviceDetail(
            @McpToolParam(description = "通道编码列表,逗号分隔,可不传查全部") String channelCodeList
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + DEVICE_DETAIL_URL;
        // 该接口是POST方式,producerCode/protocolVersion要放请求体里
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", DAHUA_PRODUCER_CODE);
        body.put("protocolVersion", DAHUA_PROTOCOL_VERSION);
        body.put("deviceCode", "");
        body.put("deviceCodeList", new ArrayList<>());
        List<String> channelCodes = new ArrayList<>();
        if (channelCodeList != null && !channelCodeList.isEmpty()) {
            for (String code : channelCodeList.split(",")) {
                if (!code.trim().isEmpty()) {
                    channelCodes.add(code.trim());
                }
            }
        }
        body.put("channelCodeList", channelCodes);
        Object result = platformApiCallServiceImpl.doPostCall(url, body);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getAlarmView",
            description = TOOL_DESC_PREFIX + """
            查询AI告警总览统计,返回值包含:
            - todayCount: 今日告警数
            - yearCount: 累计告警数
            - alarmTypeCount: 算法类型数
            - accuracy: 识别准确率(百分比数值)
            请根据用户问题(如"今天AI告警多少"/"累计告警多少"/"识别准确率多少")从返回中提取对应信息回答。
            """
    )
    public String getAlarmView() {
        String url = platformApiCallServiceImpl.getBaseUrl() + AI_ALARM_VIEW_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getAlertSituation",
            description = TOOL_DESC_PREFIX + """
            查询AI告警趋势,返回近7天(last7Days)与近30天(last30Days)两组按日期的告警数量序列,每项包含:
            - date: 日期
            - count: 当日AI告警数量
            适合回答"近7天/近30天告警走势"类问题;startTime/endTime为可选的ISO格式时间字符串,传入则按该区间查询。
            """
    )
    public String getAlertSituation(
            @McpToolParam(description = "查询开始时间,ISO格式(如2026-09-01T00:00:00),可不传") String startTime,
            @McpToolParam(description = "查询结束时间,ISO格式(如2026-09-15T23:59:59),可不传") String endTime
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + ALERT_SITUATION_URL;
        List<String> queryParams = new ArrayList<>();
        if (startTime != null && !startTime.isEmpty()) {
            queryParams.add("startTime=" + URLEncoder.encode(startTime, StandardCharsets.UTF_8));
        }
        if (endTime != null && !endTime.isEmpty()) {
            queryParams.add("endTime=" + URLEncoder.encode(endTime, StandardCharsets.UTF_8));
        }
        if (!queryParams.isEmpty()) {
            url += "?" + queryParams.stream().collect(Collectors.joining("&"));
        }
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getAlarmListWithType",
            description = TOOL_DESC_PREFIX + """
            查询分类AI告警明细,返回按类别分组的告警列表,key为类别名(安防告警/管理预警/环境预警),每个告警项包含:
            - alarmTypeName: 告警名称
            - alarmDate: 告警时间
            - alarmPosition: 告警位置
            - handleStat: 处理状态(0未处理/1已处理)
            - alarmGrade: 告警等级(1一般/2重要,更大更严重)
            startTime/endTime可选,格式yyyy-MM-dd;
            请根据用户问题(如"安防告警有哪些"/"最近环境预警明细")从返回中提取对应类别与信息回答。
            """
    )
    public String getAlarmListWithType(
            @McpToolParam(description = "查询开始时间,格式yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式yyyy-MM-dd,可不传") String endTime
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + SECURITY_ALARM_LIST_URL;
        Object result = platformApiCallServiceImpl.doPostCall(url, buildAlarmListWithTypeBody(startTime, endTime));
        return JSON.toJSONString(result);
    }
}
