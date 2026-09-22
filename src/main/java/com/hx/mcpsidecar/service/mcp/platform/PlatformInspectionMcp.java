package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 孪生巡检mcp工具集 - 来自platform平台
 */
@Service
public class PlatformInspectionMcp {

    private static final String TOOL_NAME_PREFIX = "inspection:";
    private static final String TOOL_DESC_PREFIX = "孪生巡检服务:";

    // 今日巡检概览接口URL
    public static final String INSPECTION_TODAY_OVERVIEW_URL = "/screenPatrol/todayOverview";
    // 今日任务列表接口URL
    public static final String INSPECTION_TODAY_TASK_LIST_URL = "/screenPatrol/todayTaskList";
    // 巡检统计接口URL
    public static final String INSPECTION_STATISTICS_URL = "/screenPatrol/statistics";
    // 巡检执行状态接口URL
    public static final String INSPECTION_EXECUTION_STATUS_URL = "/screenPatrol/executionStatus";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @McpTool(
            name = TOOL_NAME_PREFIX + "getTodayOverview",
            description = TOOL_DESC_PREFIX + """
            查询今日巡检概览,返回:
            - taskCount: 今日巡检任务数
            - pointCount: 巡检点位数
            - completionRate: 完成率
            - chartData: 今日巡检正常/异常图表,含xData(分类数组)/yData1(正常数数组)/yData2(异常数数组)
            即大屏"今日巡检"模块的数据;
            请根据用户问题(如"今天有多少巡检任务"/"今日巡检完成率多少")从返回中提取对应信息回答。
            """
    )
    public String getTodayOverview() {
        String url = platformApiCallServiceImpl.getBaseUrl() + INSPECTION_TODAY_OVERVIEW_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getTodayTaskList",
            description = TOOL_DESC_PREFIX + """
            查询今日巡检任务列表,返回任务数组,每项包含:
            - name: 任务名称
            - type: 任务类型
            - state: 任务状态文本(如已完成/进行中/未开始)
            - stateClass: 状态样式类,仅用于区分状态展示
            - team: 执行班组
            - time: 巡检时间
            即大屏"今日任务"模块的数据;
            请根据用户问题(如"今天有哪些巡检任务"/"今天的巡检任务由哪个班组执行")从返回中提取对应信息回答。
            """
    )
    public String getTodayTaskList() {
        String url = platformApiCallServiceImpl.getBaseUrl() + INSPECTION_TODAY_TASK_LIST_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getStatistics",
            description = TOOL_DESC_PREFIX + """
            查询巡检统计,返回:
            - avgTime: 平均巡检时长
            - pointCount: 巡检点位数
            - hazardCount: 隐患数
            - chartData: 巡检统计饼图数组,每项含 value(数值)/name(名称)
            type参数:1=近一月(默认),2=近三月,3=近一年;
            请根据用户问题(如"近三个月发现多少隐患"/"平均巡检时长多久")从返回中提取对应信息回答。
            """
    )
    public String getStatistics(
            @McpToolParam(description = "统计范围:1=近一月,2=近三月,3=近一年,可不传默认近一月") String type
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + INSPECTION_STATISTICS_URL;
        if (type != null && !type.isEmpty()) {
            url += "?type=" + type;
        }
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getExecutionStatus",
            description = TOOL_DESC_PREFIX + """
            查询巡检执行状态(按人员统计),返回:
            - xData: 巡检人员数组
            - yData1: 各人员巡检正常次数数组,与xData一一对应
            - yData2: 各人员巡检异常次数数组,与xData一一对应
            即大屏"巡检执行状态"模块的数据;
            请根据用户问题(如"谁的巡检异常最多"/"各人员巡检正常情况怎么样")从返回中提取对应信息回答。
            """
    )
    public String getExecutionStatus() {
        String url = platformApiCallServiceImpl.getBaseUrl() + INSPECTION_EXECUTION_STATUS_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }
}
