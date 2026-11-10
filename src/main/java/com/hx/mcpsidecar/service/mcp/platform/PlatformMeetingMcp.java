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
 * 会议管理mcp工具集 - 来自platform平台
 */
@Service
public class PlatformMeetingMcp {

    private static final String TOOL_NAME_PREFIX = "meeting:";
    private static final String TOOL_DESC_PREFIX = "会议管理服务:";

    // 会议统计接口URL
    public static final String MEETING_STATISTICS_URL = "/meeting/meetingStatistics";
    // 高频会议室接口URL
    public static final String HIGH_FREQUENCY_MEETING_ROOMS_URL = "/meeting/highFrequencyMeetingRooms";
    // 会议室一览接口URL
    public static final String MEETING_ROOM_OVERVIEW_URL = "/meeting/meetingRoomOverview";
    // 会议数量接口URL
    public static final String NUMBER_OF_MEETINGS_URL = "/meeting/numberOfMeetings";
    // 会议室状态接口URL
    public static final String MEETING_ROOM_STATUS_URL = "/meeting/meetingRoomStatus";
    // 当日会议接口URL
    public static final String ROOM_MEET_LIST_BY_DAY_URL = "/meetBook/getRoomMeetListByDay";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getMeetingStatistics",
            description = TOOL_DESC_PREFIX + """
            查询会议统计,返回:
            - reservations: 预约数
            - reservationsChangeRate: 预约数环比变化率
            - reservationsChangeDirection: 环比变化方向(up/down)
            - avgDuration: 平均会议时长
            - avgDurationChangeRate: 平均时长环比变化率
            - avgDurationChangeDirection: 平均时长环比变化方向(up/down)
            请根据用户问题(如"会议预约情况"/"会议统计")从返回中提取对应信息回答。
            """
    )
    public String getMeetingStatistics() {
        String url = platformApiCallServiceImpl.getBaseUrl() + MEETING_STATISTICS_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getHighFrequencyMeetingRooms",
            description = TOOL_DESC_PREFIX + """
            查询高频会议室TOP5,返回数组,每项含:
            - name: 会议室名称
            - value: 使用次数/频率
            - address: 地点
            - capacity: 容量
            请根据用户问题(如"哪个会议室用得最多"/"高频会议室")从返回中提取对应信息回答。
            """
    )
    public String getHighFrequencyMeetingRooms() {
        String url = platformApiCallServiceImpl.getBaseUrl() + HIGH_FREQUENCY_MEETING_ROOMS_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getMeetingRoomOverview",
            description = TOOL_DESC_PREFIX + """
            查询会议室一览,返回各会议室使用情况数组,每项含:
            - name: 会议室名称
            - value: 使用量/占比
            请根据用户问题(如"会议室使用情况"/"会议室一览")从返回中提取对应信息回答。
            """
    )
    public String getMeetingRoomOverview() {
        String url = platformApiCallServiceImpl.getBaseUrl() + MEETING_ROOM_OVERVIEW_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getNumberOfMeetings",
            description = TOOL_DESC_PREFIX + """
            查询会议数量,返回:
            - trendCategory: 趋势分类数组
            - trendDatas: 趋势数据数组,每项含 name/data
            - trendYMax: Y轴最大值
            - statusData: 按状态分布数组,每项含 name/value
            - typeData: 按类型分布数组,每项含 name/value
            请根据用户问题(如"会议数量趋势"/"今天多少会议")从返回中提取对应信息回答。
            """
    )
    public String getNumberOfMeetings() {
        String url = platformApiCallServiceImpl.getBaseUrl() + NUMBER_OF_MEETINGS_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getMeetingRoomStatus",
            description = TOOL_DESC_PREFIX + """
            查询会议室状态,返回:
            - pieData: 会议室状态占比,每项含 value/name/color
            - list: 各会议室使用/空闲明细,每项含 name/used/free
            请根据用户问题(如"会议室空闲情况"/"哪些会议室在用")从返回中提取对应信息回答。
            """
    )
    public String getMeetingRoomStatus() {
        String url = platformApiCallServiceImpl.getBaseUrl() + MEETING_ROOM_STATUS_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getRoomMeetListByDay",
            description = TOOL_DESC_PREFIX + """
            查询当日会议安排,返回数组,每项含:
            - startTime: 开始时间
            - endTime: 结束时间
            - meetName: 会议名称
            - applyUserName: 申请人
            - meetStatus: 会议状态(0/1/2/3)
            请根据用户问题(如"今天有什么会议"/"会议室今日安排")从返回中提取对应信息回答。
            """
    )
    public String getRoomMeetListByDay(
            @McpToolParam(description = "查询开始时间,格式 yyyy-MM-dd,可不传") String startTime,
            @McpToolParam(description = "查询结束时间,格式 yyyy-MM-dd,可不传") String endTime
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + ROOM_MEET_LIST_BY_DAY_URL;
        Map<String, Object> body = new HashMap<>();
        if (startTime != null && !startTime.isEmpty()) {
            body.put("startTime", startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            body.put("endTime", endTime);
        }
        Object result = platformApiCallServiceImpl.doPostCall(url, body);
        return JSON.toJSONString(result);
    }
}
