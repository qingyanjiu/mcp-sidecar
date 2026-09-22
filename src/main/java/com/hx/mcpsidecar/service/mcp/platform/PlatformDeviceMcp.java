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
 * 设备态势mcp工具集 - 来自platform平台
 */
@Service
public class PlatformDeviceMcp {

    private static final String TOOL_NAME_PREFIX = "device:";
    private static final String TOOL_DESC_PREFIX = "设备态势服务:";

    // 设备分类统计接口URL
    public static final String DEVICE_EQUIP_CLASS_URL = "/device/equipClass";
    // 类别健康度接口URL
    public static final String DEVICE_CATEGORY_HEALTH_URL = "/device/categoryHealth";
    // 月度维修趋势接口URL
    public static final String DEVICE_MONTH_MAINTENANCE_URL = "/device/monthMaintenance";
    // 月度报修趋势接口URL
    public static final String DEVICE_MONTH_REPAIR_URL = "/device/monthRepair";
    // 分区域设备统计接口URL(与孪生监控模块共用)
    public static final String MONITOR_STATIS_REGION_URL = "/monitor/statisRegion";
    // 安防设备在线率接口URL
    public static final String ANFANG_ONLINE_PERCENTAGE_URL = "/screenPage/anfang/getAnfangDeviceOnlinePercentage";
    // 广播设备在线率接口URL
    public static final String GB_ONLINE_PERCENTAGE_URL = "/calldevice/gb/onlinePercentage";
    // 门禁设备在线率接口URL
    public static final String MJ_ONLINE_PERCENTAGE_URL = "/screenPage/anfang/getMjOnlinePercentage";

    // 三方安防协议接口必传参数:调用方标识(生产者编码)
    public static final String DAHUA_PRODUCER_CODE = "dahua";
    // 三方安防协议接口必传参数:协议版本号
    public static final String DAHUA_PROTOCOL_VERSION = "V5.0.16";
    // 三方广播协议接口必传参数:调用方标识(生产者编码)
    public static final String ITC_PRODUCER_CODE = "itc";
    // 三方广播协议接口必传参数:协议版本号
    public static final String ITC_PROTOCOL_VERSION = "V1.0";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getEquipClass",
            description = TOOL_DESC_PREFIX + """
            查询设备分类数量占比,返回数组,每项包含:
            - name: 设备类别名称(如消防/空调/能耗/门禁等)
            - value: 该类别设备数量
            - itemStyle.color: 该类别的展示颜色
            全部类别的value合计即设备总数;
            请根据用户问题(如"园区有多少设备"/"哪类设备最多"/"各类设备占比")从返回中提取对应信息回答。
            """
    )
    public String getEquipClass() {
        String url = platformApiCallServiceImpl.getBaseUrl() + DEVICE_EQUIP_CLASS_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getCategoryHealth",
            description = TOOL_DESC_PREFIX + """
            查询各类别设备健康度,返回allDevices数组,每项包含:
            - name: 设备类别名称
            - score: 健康度评分
            - onlineRate: 在线率,含 current(在线数)/total(总数)/percent(百分比)
            - maintenanceRate: 维保率,含 percent(百分比)
            - lifeRate: 寿命比例,含 current/total/percent
            请根据用户问题(如"消防设备健康度怎么样"/"哪类设备健康度最低")从返回中提取对应信息回答。
            """
    )
    public String getCategoryHealth() {
        String url = platformApiCallServiceImpl.getBaseUrl() + DEVICE_CATEGORY_HEALTH_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getMonthMaintenance",
            description = TOOL_DESC_PREFIX + """
            查询月度维修量趋势,返回:
            - xData: X轴标签数组(月份)
            - series: 维修量序列数组,每项含 name(序列名,如计划维修/实际维修)与 data(与xData对应的数量数组)
            date参数可选,格式yyyy-MM,不传默认当月;
            请根据用户问题(如"本月维修了多少设备"/"维修量趋势")从返回中提取对应信息回答。
            """
    )
    public String getMonthMaintenance(
            @McpToolParam(description = "查询月份,格式yyyy-MM,可不传默认当月") String date
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + DEVICE_MONTH_MAINTENANCE_URL;
        if (date != null && !date.isEmpty()) {
            url += "?date=" + date;
        }
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getMonthRepair",
            description = TOOL_DESC_PREFIX + """
            查询月度报修量趋势,返回:
            - xData: X轴标签数组(当月各天)
            - series: 报修量序列数组,每项含 name(报修来源,如和信/设计院)与 data(与xData对应的数量数组)
            date参数可选,格式yyyy-MM,不传默认当月;
            请根据用户问题(如"本月报修了多少次"/"哪天报修最多")从返回中提取对应信息回答。
            """
    )
    public String getMonthRepair(
            @McpToolParam(description = "查询月份,格式yyyy-MM,可不传默认当月") String date
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + DEVICE_MONTH_REPAIR_URL;
        if (date != null && !date.isEmpty()) {
            url += "?date=" + date;
        }
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getStatisRegion",
            description = TOOL_DESC_PREFIX + """
            查询各区域设备数量统计(柱状图数据),返回:
            - xAxisNameMap: 区域名称数组
            - VALUE: 与区域对应的设备数量数组
            - MAX: 与区域对应的最大值数组(用于图表刻度)
            deviceType可选,用于按设备类型过滤(不传统计全部类型);
            请根据用户问题(如"哪个区域设备最多"/"各区域设备分布")从返回中提取对应信息回答。
            """
    )
    public String getStatisRegion(
            @McpToolParam(description = "设备类型编码,可不传统计全部类型") String deviceType
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + MONITOR_STATIS_REGION_URL;
        if (deviceType != null && !deviceType.isEmpty()) {
            url += "?deviceType=" + deviceType;
        }
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getAnfangDeviceOnlinePercentage",
            description = TOOL_DESC_PREFIX + """
            查询安防设备在线率,返回各分类的在线统计:
            - total: 全部安防设备汇总,含 totalNum(总数)/onlineNum(在线数)/onlinePercentage(在线率百分比)
            - zhoujie/dz/mj: 各设备子类(如周界/门禁等)的在线统计,字段同total
            请根据用户问题(如"安防设备在线率多少"/"有多少安防设备离线")从返回的total中提取对应信息回答。
            """
    )
    public String getAnfangDeviceOnlinePercentage() {
        String url = platformApiCallServiceImpl.getBaseUrl() + ANFANG_ONLINE_PERCENTAGE_URL;
        Object result = platformApiCallServiceImpl.doPostCall(url, buildDahuaBody());
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getGbOnlinePercentage",
            description = TOOL_DESC_PREFIX + """
            查询广播设备在线率,返回:
            - totalCount: 广播设备总数
            - onlineCount: 在线数量
            - percentage: 在线率百分比
            请根据用户问题(如"广播设备在线率多少"/"有多少广播设备离线")从返回中提取对应信息回答。
            """
    )
    public String getGbOnlinePercentage() {
        String url = platformApiCallServiceImpl.getBaseUrl() + GB_ONLINE_PERCENTAGE_URL;
        // 该接口是POST方式,producerCode/protocolVersion要放请求体里
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", ITC_PRODUCER_CODE);
        body.put("protocolVersion", ITC_PROTOCOL_VERSION);
        Object result = platformApiCallServiceImpl.doPostCall(url, body);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getMjOnlinePercentage",
            description = TOOL_DESC_PREFIX + """
            查询门禁设备在线率,返回:
            - totalNum: 门禁设备总数
            - onlineNum: 在线数量
            - onlinePercentage: 在线率百分比
            请根据用户问题(如"门禁设备在线率多少"/"有多少门禁离线")从返回中提取对应信息回答。
            """
    )
    public String getMjOnlinePercentage() {
        String url = platformApiCallServiceImpl.getBaseUrl() + MJ_ONLINE_PERCENTAGE_URL;
        Object result = platformApiCallServiceImpl.doPostCall(url, buildDahuaBody());
        return JSON.toJSONString(result);
    }

    /**
     * 大华安防协议(dahua/V5.0.16)请求体:producerCode/protocolVersion放body,分页参数取全量
     */
    private static Map<String, Object> buildDahuaBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", DAHUA_PRODUCER_CODE);
        body.put("protocolVersion", DAHUA_PROTOCOL_VERSION);
        body.put("pageNum", 1);
        body.put("pageSize", 1000);
        return body;
    }
}
