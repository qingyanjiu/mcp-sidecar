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
 * 消防态势mcp工具集 - 来自platform平台
 */
@Service
public class PlatformFireMcp {

    private static final String TOOL_NAME_PREFIX = "fire:";
    private static final String TOOL_DESC_PREFIX = "消防态势服务:";

    // 消防设备台账接口URL
    public static final String FIRE_ASSETS_URL = "/calldevice/fire/getAssetsList";
    // 设备告警统计接口URL
    public static final String FIRE_ALARM_NUM_URL = "/calldevice/fire/getAlarmNum";
    // 实时消防告警列表接口URL
    public static final String FIRE_ALARM_LIST_URL = "/calldevice/fire/getAlarmList";
    // 月度报修趋势接口URL
    public static final String FIRE_MONTH_REPAIR_URL = "/fire/monthRepair";
    // 三方消防协议接口必传参数:调用方标识(生产者编码)
    public static final String SJY_PRODUCER_CODE = "sjy";
    // 三方消防协议接口必传参数:协议版本号
    public static final String SJY_PROTOCOL_VERSION = "V1.0";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getFireAssets",
            description = TOOL_DESC_PREFIX + """
            查询消防设备台账,返回分页结果,rows为设备数组,每项包含:
            - assetsName: 设备名称(如压力表/消火栓/液位表/传输装置)
            - offLineFlag: 在离线标志,"1"或"2"=在线,其他=离线
            - faultFlag: 故障标志
            - usageFlag: 使用状态标志
            - hiddenDangerFlag: 隐患标志
            - dataMap: 实时监测数据数组,每项含 monitorName(监测项,如压力/液位/电量/倾角)/monitorValue(数值)/unit(单位)
            - deviceCode: 设备编码
            - assetsId: 设备资产ID
            total为设备总数。assetsName支持模糊过滤(如传"消火栓"只查消火栓),不传查全部;
            请根据用户问题(如"消火栓压力正常吗"/"有哪些消防设备离线")从返回中提取对应信息回答。
            """
    )
    public String getFireAssets(
            @McpToolParam(description = "设备名称,支持模糊查询,可不传查全部") String assetsName
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + FIRE_ASSETS_URL;
        // 该接口是POST方式,producerCode/protocolVersion要放请求体里
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", SJY_PRODUCER_CODE);
        body.put("protocolVersion", SJY_PROTOCOL_VERSION);
        body.put("assetsName", assetsName == null ? "" : assetsName);
        body.put("deviceCode", "");
        body.put("usageFlag", "");
        body.put("offLineFlag", "");
        body.put("pageNum", 1);
        body.put("pageSize", 1000);
        Object result = platformApiCallServiceImpl.doPostCall(url, body);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getFireAlarmNum",
            description = TOOL_DESC_PREFIX + """
            查询消防设备告警分类统计,返回:
            - allAlarmNum: 告警总数
            - fireNum: 火灾告警数
            - fireMisreportNum: 火灾误报数
            - fireDangerNum: 火灾隐患告警数
            - fireDangerMisreportNum: 火灾隐患误报数
            - deviceFaultNum: 设备故障告警数
            - deviceFaultMisreportNum: 设备故障误报数
            - deviceStateNum: 设备状态告警数
            - userLeaveNum: 离人告警数
            days为统计近多少天的数据,大屏常用7/30/365;
            请根据用户问题(如"本月火灾告警多少次"/"近一周消防误报多不多")从返回中提取对应信息回答。
            """
    )
    public String getFireAlarmNum(
            @McpToolParam(description = "统计近多少天的数据,默认7") Integer days
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + FIRE_ALARM_NUM_URL;
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", SJY_PRODUCER_CODE);
        body.put("protocolVersion", SJY_PROTOCOL_VERSION);
        long[] range = toTimeRange(days);
        // 该接口时间参数为毫秒时间戳字符串
        body.put("startTime", String.valueOf(range[0]));
        body.put("endTime", String.valueOf(range[1]));
        Object result = platformApiCallServiceImpl.doPostCall(url, body);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getFireAlarmList",
            description = TOOL_DESC_PREFIX + """
            查询实时消防告警列表,返回分页结果,rows为告警数组,每项包含:
            - title: 告警标题
            - alarmTypeName: 告警类型名
            - assetsName: 告警设备名称
            - areaName: 告警区域
            - nowAlarmTime: 最近告警时间(毫秒时间戳)
            - firstAlarmTime: 首次告警时间(毫秒时间戳)
            - alarmLevel: 告警等级
            - handleStatus: 处理状态
            - alarmReason: 告警原因
            - deviceCode/alarmCode/alarmType/assetsId: 各类编码
            total为告警总数。days为查近多少天的告警,默认7天;
            请根据用户问题(如"最近有什么火警"/"消防告警在哪个区域")从返回中提取对应信息回答。
            """
    )
    public String getFireAlarmList(
            @McpToolParam(description = "查询近多少天的告警,默认7") Integer days
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + FIRE_ALARM_LIST_URL;
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", SJY_PRODUCER_CODE);
        body.put("protocolVersion", SJY_PROTOCOL_VERSION);
        long[] range = toTimeRange(days);
        // 该接口时间参数为毫秒时间戳字符串
        body.put("startTime", String.valueOf(range[0]));
        body.put("endTime", String.valueOf(range[1]));
        body.put("pageNum", 1);
        body.put("pageSize", 100);
        Object result = platformApiCallServiceImpl.doPostCall(url, body);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getMonthRepair",
            description = TOOL_DESC_PREFIX + """
            查询消防月度报修趋势,返回:
            - category: X轴月份分类数组
            - oneData: 和信报修数量序列
            - twoData: 设计院报修数量序列
            date参数可选,格式yyyy-MM-dd,不传默认当月;
            请根据用户问题(如"消防报修趋势"/"哪个月报修最多")从返回中提取对应信息回答。
            """
    )
    public String getMonthRepair(
            @McpToolParam(description = "查询日期,格式yyyy-MM-dd,可不传默认当月") String date
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + FIRE_MONTH_REPAIR_URL;
        if (date != null && !date.isEmpty()) {
            url += "?date=" + date;
        }
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    /**
     * 将"近N天"转换为毫秒时间戳范围:返回 [开始时间, 结束时间]。
     * days为空或非正数时默认取7天(与大屏默认一致)。
     */
    private long[] toTimeRange(Integer days) {
        long end = System.currentTimeMillis();
        int n = (days == null || days <= 0) ? 7 : days;
        return new long[]{end - n * 24L * 60 * 60 * 1000, end};
    }
}
