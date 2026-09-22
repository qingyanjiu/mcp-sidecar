package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.mock.MockData;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 孪生周界mcp工具集 - 来自platform平台
 */
@Service
public class PlatformPerimeterMcp {

    private static final String TOOL_NAME_PREFIX = "perimeter:";
    private static final String TOOL_DESC_PREFIX = "孪生周界服务:";

    // 关键指标接口URL
    public static final String PERIMETER_KEY_METRICS_URL = "/perimeter/keyMetrics";
    // 防区一览接口URL
    public static final String PERIMETER_AREA_OVERVIEW_URL = "/perimeter/areaOverview";
    // 周界告警统计接口URL
    public static final String PERIMETER_ALARM_STATS_URL = "/perimeter/perimeterAlarmStats";
    // 告警一览接口URL
    public static final String PERIMETER_ALARM_OVERVIEW_URL = "/perimeter/alarmOverview";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getKeyMetrics",
            description = TOOL_DESC_PREFIX + """
            查询周界防区关键指标,返回:
            - onlineDevices: 在线防区设备数
            - offlineDevices: 离线防区设备数
            - todayAlarmCount: 今日周界告警数
            即大屏"关键指标"卡片的数据;
            请根据用户问题(如"今天周界告警多少次"/"有几个防区设备离线")从返回中提取对应信息回答。
            """
    )
    public String getKeyMetrics() {
        String url = platformApiCallServiceImpl.getBaseUrl() + PERIMETER_KEY_METRICS_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getAreaOverview",
            description = TOOL_DESC_PREFIX + """
            查询周界防区一览,返回防区数组,每项包含:
            - code: 防区编码
            - name: 防区名称
            - status: 布防状态,"0"=离线,"1"=布防,"2"=撤防,"3"=告警(其中离线为独立状态;其余均在线)
            即大屏"防区一览"模块的数据(布防/撤防开关在UE端3D联动,MCP仅查询);
            请根据用户问题(如"哪些防区已布防"/"有没有防区在告警")从返回中提取对应信息回答。
            """
    )
    public String getAreaOverview() {
        String url = platformApiCallServiceImpl.getBaseUrl() + PERIMETER_AREA_OVERVIEW_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getPerimeterAlarmStats",
            description = TOOL_DESC_PREFIX + """
            查询周界告警统计,返回:
            - areaStats: 告警区域统计数组,每项含 name(防区名)/num(告警次数),按次数排序
            - timeStats: 告警高峰统计数组,每项含 timeduration(时段,如"08:00-10:00")/num(告警次数)
            - hourlyData: 全天告警数量曲线,含 category(小时分类数组)/datas(各小时告警次数数组)
            date参数格式yyyy-MM-dd,不传默认当天;
            请根据用户问题(如"哪个防区告警最多"/"周界告警高峰在几点")从返回中提取对应信息回答。
            """
    )
    public String getPerimeterAlarmStats(
            @McpToolParam(description = "统计日期,格式yyyy-MM-dd,可不传默认当天") String date
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + PERIMETER_ALARM_STATS_URL;
        if (date != null && !date.isEmpty()) {
            url += "?date=" + date;
        }
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getAlarmOverview",
            description = TOOL_DESC_PREFIX + """
            查询周界告警一览,返回告警数组,每项包含:
            - status: 告警状态标志
            - title: 告警标题(如"周界入侵")
            - channelName: 告警通道名(触发告警的通道/点位,可能缺失)
            - date: 告警时间
            - content: 告警内容描述
            - pic: 抓拍照片地址(点击告警项可查看)
            即大屏"告警一览"模块的数据;
            请根据用户问题(如"最近周界有什么告警"/"哪个点位被入侵")从返回中提取对应信息回答。
            """
    )
    public String getAlarmOverview() {
        String url = platformApiCallServiceImpl.getBaseUrl() + PERIMETER_ALARM_OVERVIEW_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }
}
