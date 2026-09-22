package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.mock.MockData;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备查询mcp工具集 - 来自platform平台
 *
 * <p>四组工具,全部挂在 {@code /mcp/devicequery} 端点上:</p>
 * <ol>
 *   <li><b>设备资产库查询</b> —— {@code /deviceInfo/*},查平台自有的 {@code VISUAL_DEVICE_INFO} 纳管资产库。
 *       设备清单、资产统计走这组。</li>
 *   <li><b>设备状态与类型统计</b> —— 按类型/状态/空间/楼层等维度聚合。注意这里的"状态"是
 *       <b>启用/停用</b>口径,不是在线/离线。</li>
 *   <li><b>设备在线率</b> —— 跨 {@code energy}/{@code device}/{@code anfang} 三处,
 *       各接口的在线判定口径不同,描述里逐个写明了。</li>
 *   <li><b>厂商平台实时设备列表</b> —— {@code /calldevice/*} 透传大华 ICC 等厂商协议,
 *       不查库。与前三组口径不同,见 {@link #getVendorDeviceList} 的说明。</li>
 * </ol>
 *
 * <p><b>口径是这个模块最大的坑</b>:多个接口的字段名叫 {@code online}/{@code offline},
 * 实际统计的是 {@code status} 启用/停用。真正的在线数据只有能耗在线表和安防厂商实时接口两处。
 * 每个工具的描述里都标了口径,新增工具时请沿用这个习惯。</p>
 *
 * <p><b>与 {@code /mcp/device} 端点的重叠</b>:下面 5 个接口在
 * {@link PlatformDeviceMcp} 里已经各有一个工具,行为完全相同,是刻意保留的重复
 * (两套并存于不同端点)。工具描述里已注明"与 device:xxx 同一接口,任选其一",
 * 不要再新增重叠工具,也不要让两边改出差异:</p>
 * <ul>
 *   <li>{@code /device/equipClass} —— device:getEquipClass</li>
 *   <li>{@code /monitor/statisRegion} —— device:getStatisRegion</li>
 *   <li>{@code /device/categoryHealth} —— device:getCategoryHealth</li>
 *   <li>{@code /screenPage/anfang/getMjOnlinePercentage} —— device:getMjOnlinePercentage</li>
 *   <li>{@code /screenPage/anfang/getAnfangDeviceOnlinePercentage} —— device:getAnfangDeviceOnlinePercentage</li>
 * </ul>
 */
@Service
public class PlatformDeviceQueryMcp {

    private static final String TOOL_NAME_PREFIX = "device_query:";
    private static final String TOOL_DESC_PREFIX = "设备查询服务:";

    // ==================================================================
    // URL 常量
    // ==================================================================

    // ---------- 一、设备资产库查询(deviceInfo) ----------
    /** 设备列表(主力接口,分页 + 字典翻译 deviceTypeName) */
    public static final String DEVICE_INFO_LIST_URL = "/deviceInfo/list";
    /** 设备列表(不分页,返回裸数组,不翻译 deviceTypeName) */
    public static final String DEVICE_INFO_LIST_ONLY_URL = "/deviceInfo/listOnly";
    /** 按空间查下级设备(支持多设备类型,不分页) */
    public static final String DEVICE_INFO_WITH_SPACE_URL = "/deviceInfo/selectListWithSpace";
    /** 设备详情(按内部 id) */
    public static final String DEVICE_INFO_DETAIL_URL = "/deviceInfo/getDetail";
    /** 设备统计列表(单位 × 部门 × 类型,带分页) */
    public static final String DEVICE_INFO_STATISTICS_URL = "/deviceInfo/queryDeviceStatistics";

    // ---------- 二、设备状态与类型统计 ----------
    /** 按设备类型统计数量 */
    public static final String TYPE_NUM_STATISTIC_URL = "/deviceInfo/typeNumStatistic";
    /** 按启用状态统计数量 */
    public static final String STATUS_NUM_STATISTIC_URL = "/deviceInfo/statusNumStatistic";
    /** 按 类型 × 状态 交叉统计 */
    public static final String ALL_STATUS_NUM_STATISTIC_URL = "/deviceInfo/allDeviceStatusNumStatistic";
    /** 按空间位置统计(排除停用设备) */
    public static final String SPACE_NUM_STATISTIC_URL = "/deviceInfo/spaceNumStatistic";
    /** 按楼层统计(无设备的楼层也返回 0) */
    public static final String FLOOR_NUM_STATISTIC_URL = "/deviceInfo/floorNumStatistic";
    /** 监控设备按子类型统计 */
    public static final String MONITOR_TYPE_STATISTIC_URL = "/deviceInfo/monitorTypeStatisticInSpace";
    /** 监控设备按状态统计 */
    public static final String MONITOR_STATUS_STATISTIC_URL = "/deviceInfo/monitorStatusStatisticInSpace";
    /** 设备分布(单位 × 部门 × 类型,带排名) */
    public static final String DISTRIBUTION_URL = "/deviceInfo/distribution";
    /** 按类型分类统计(启用/停用) */
    public static final String QUERY_DEVICE_BY_PARAM_URL = "/deviceInfo/queryDeviceByParam";
    /** 设备分类饼图(该 Controller 无类级路径前缀) */
    public static final String EQUIP_CLASS_URL = "/device/equipClass";
    /** 分区域设备数量统计(该 Controller 无类级路径前缀) */
    public static final String MONITOR_STATIS_REGION_URL = "/monitor/statisRegion";

    // ---------- 三、设备在线率 ----------
    /** 能耗设备在线率(唯一真实在线表) */
    public static final String ENERGY_ONLINE_RATE_URL = "/energy/onlineRate";
    /** 能耗设备在线状态汇总(电表 + 水表) */
    public static final String ENERGY_DEVICE_STATUS_URL = "/energy/deviceStatus";
    /** 设备类别健康度(含在线率,安防部分为启用口径) */
    public static final String CATEGORY_HEALTH_URL = "/device/categoryHealth";
    /** 设备分布(按楼层"在线"/离线,实为启用口径) */
    public static final String EQUIP_DISTRIBUTION_URL = "/device/equipDistribution";
    /** 监控设备在线率(厂商实时,唯一真实监控在线数据) */
    public static final String CAMERA_ONLINE_PERCENTAGE_URL = "/screenPage/anfang/getCameraOnlinePercentage";
    /** 门禁设备在线率(厂商实时) */
    public static final String MJ_ONLINE_PERCENTAGE_URL = "/screenPage/anfang/getMjOnlinePercentage";
    /** 安防设备在线状态(周界 + 门禁,厂商实时) */
    public static final String ANFANG_ONLINE_PERCENTAGE_URL = "/screenPage/anfang/getAnfangDeviceOnlinePercentage";

    // ---------- 四、厂商平台实时设备列表(calldevice 透传) ----------
    // 监控设备列表接口URL(大华;服务端硬编码 types = {"1_10"})
    public static final String JK_DEVICE_LIST_URL = "/calldevice/jk/getDeviceList";
    // 门禁设备列表接口URL(大华;服务端硬编码 categorys = {"8"})
    public static final String MJ_DEVICE_LIST_URL = "/calldevice/mj/getDeviceList";
    // 道闸设备列表接口URL(大华;服务端硬编码 categorys = {"35"})
    public static final String DZ_DEVICE_LIST_URL = "/calldevice/dz/getDeviceList";
    // 广播设备列表接口URL(ITC;返回体为 data.EndPointsArray[])
    public static final String GB_DEVICE_LIST_URL = "/calldevice/gb/getDeviceList";
    // 信息发布设备列表接口URL(和信;服务层无参,请求体被忽略,每次全量返回)
    public static final String XXFB_DEVICE_LIST_URL = "/calldevice/xxfb/getDeviceList";
    // 按设备编号查设备详情接口URL(GET,code 走 query;属于设备资产模块,不在 calldevice 系列下)
    public static final String DEVICE_DETAIL_BY_CODE_URL = "/visual/getDeviceDetailByCode";

    // ==================================================================
    // 三方协议常量
    // ==================================================================

    // 三方协议接口必传参数:大华(监控/门禁/道闸)
    public static final String DAHUA_PRODUCER_CODE = "dahua";
    public static final String DAHUA_PROTOCOL_VERSION = "V5.0.16";
    // 三方协议接口必传参数:ITC(广播)
    public static final String ITC_PRODUCER_CODE = "itc";
    public static final String ITC_PROTOCOL_VERSION = "V1.0";
    // 三方协议接口必传参数:和信(信息发布)
    public static final String HX_PRODUCER_CODE = "hx";
    public static final String HX_PROTOCOL_VERSION = "V4.8.5";

    // ==================================================================
    // 默认值
    // ==================================================================

    // 分页参数(数字型,deviceInfo 系列用)
    private static final int DEFAULT_PAGE_CURRENT = 1;
    // deviceInfo/list 平台默认每页 10 条,对问答太小;给 100 既能答"有哪些设备"又不会一次拉全表
    private static final int DEFAULT_PAGE_SIZE = 100;
    // 厂商透传接口的分页参数是字符串
    private static final String DEFAULT_VENDOR_PAGE_NUM = "1";
    // 默认取1000条:一次覆盖绝大多数设备清单,省得为凑全量反复翻页
    private static final String DEFAULT_VENDOR_PAGE_SIZE = "1000";
    // 厂商在线率接口只统计"当前页",想要准确的在线率就得把一页撑大
    private static final String ONLINE_RATE_PAGE_SIZE = "1000";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    // ==================================================================
    // 一、设备资产库查询(/deviceInfo/*)
    // ==================================================================

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "listDevice",
            description = TOOL_DESC_PREFIX + """
            分页查询平台纳管的设备资产清单(主力接口)。查"有哪些设备"/"设备清单"/"某类的设备"用它。
            返回 data 为 {page:{total(总数), size, pages, current}, data:[设备数组]},每项含:
            - id: 设备内部ID(查详情要用它)
            - name: 设备名称;code: 设备编号
            - syncSource: 设备类型编码(见下);deviceTypeName: 类型中文名(本接口已翻译)
            - status: 启用状态(0停用 1启用 2维修 3报废) —— 注意这不是在线状态
            - spaceId/spaceName: 所在空间ID/全路径名(如 园区/北门/门岗)
            - personInChargeName: 负责人;orgName: 所属组织
            - maintained: 是否已维护(0否 1是)
            筛选参数都是可选的,传几个就按几个过滤:
            - syncSource: 设备类型编码,精确匹配。0门禁 1道闸 2梯控 3监控 4入侵报警 5广播 6水表 7电表
            - deviceType: 设备子类型,精确匹配(和 syncSource 不是一回事:syncSource 是"哪一类设备",
              deviceType 是这一类下面的细分;一般用 syncSource 就够)
            - status: 启用状态,精确匹配
            - name: 设备名称,模糊匹配;code: 设备编号,精确匹配
            - spaceId: 空间ID,会连带查出该空间下级的设备;传 -1 表示不带这个条件
            - maintained: 是否已维护
            请根据用户问题(如"列出所有门禁设备"/"北区有哪些设备"/"叫摄像头的设备")决定传哪些条件,
            从返回的 page.total 回答数量类问题,从 data 数组回答明细类问题。
            """
    )
    public String listDevice(
            @McpToolParam(description = "设备类型编码,精确匹配。0门禁 1道闸 2梯控 3监控 4入侵报警 5广播 6水表 7电表", required = false) String syncSource,
            @McpToolParam(description = "设备子类型,精确匹配,一般不用传", required = false) String deviceType,
            @McpToolParam(description = "启用状态,精确匹配。0停用 1启用 2维修 3报废", required = false) String status,
            @McpToolParam(description = "设备名称,模糊匹配", required = false) String name,
            @McpToolParam(description = "设备编号,精确匹配", required = false) String code,
            @McpToolParam(description = "空间位置ID,会连带查下级设备;传 -1 表示不带该条件", required = false) String spaceId,
            @McpToolParam(description = "是否已维护。0否 1是", required = false) String maintained,
            @McpToolParam(description = "页码,从1开始,可不传默认1", required = false) Integer pageCurrent,
            @McpToolParam(description = "每页条数,可不传默认100", required = false) Integer pageSize
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("page", page(pageCurrent, pageSize));
        body.put("data", cond(
                "syncSource", syncSource,
                "deviceType", deviceType,
                "status", status,
                "name", name,
                "code", code,
                "spaceId", spaceId,
                "maintained", maintained));
        Object result = platformApiCallServiceImpl.doPostCall(base(DEVICE_INFO_LIST_URL), body);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "listDeviceOnly",
            description = TOOL_DESC_PREFIX + """
            查询设备资产清单,不分页,返回裸数组(全部符合条件的设备)。
            与 listDevice 的差异,选之前先想清楚:
            - 不分页:有多少返回多少,设备多的时候返回体很大,容易吃掉上下文
            - 不翻译:没有 deviceTypeName 字段,只有 syncSource 编码,类型中文名要自己对照
              (0门禁 1道闸 2梯控 3监控 4入侵报警 5广播 6水表 7电表)
            - 返回的字段其余同 listDevice
            定不到具体条数、又确实需要全量时才有必要用它;能用 listDevice 分页就用 listDevice。
            筛选参数含义同 listDevice。
            """
    )
    public String listDeviceOnly(
            @McpToolParam(description = "设备类型编码,精确匹配。0门禁 1道闸 2梯控 3监控 4入侵报警 5广播 6水表 7电表", required = false) String syncSource,
            @McpToolParam(description = "设备子类型,精确匹配,一般不用传", required = false) String deviceType,
            @McpToolParam(description = "启用状态,精确匹配。0停用 1启用 2维修 3报废", required = false) String status,
            @McpToolParam(description = "设备名称,模糊匹配", required = false) String name,
            @McpToolParam(description = "设备编号,精确匹配", required = false) String code,
            @McpToolParam(description = "空间位置ID,会连带查下级设备;传 -1 表示不带该条件", required = false) String spaceId,
            @McpToolParam(description = "是否已维护。0否 1是", required = false) String maintained
    ) {
        Map<String, Object> body = cond(
                "syncSource", syncSource,
                "deviceType", deviceType,
                "status", status,
                "name", name,
                "code", code,
                "spaceId", spaceId,
                "maintained", maintained);
        Object result = platformApiCallServiceImpl.doPostCall(base(DEVICE_INFO_LIST_ONLY_URL), body);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "listDeviceWithSpace",
            description = TOOL_DESC_PREFIX + """
            按空间位置查询该节点下所有下级的设备,不分页,返回数组。
            设备类型要传多个时用 deviceTypeList,例如同时要门禁和监控就传 ["0","3"]。
            类型编码:0门禁 1道闸 2梯控 3监控 4入侵报警 5广播 6水表 7电表。
            返回字段同 listDevice,但不含 deviceTypeName(没有类型中文名翻译)。
            与 listDevice 的分工:想按类型/状态/名称筛设备用 listDevice;
            想"把某个区域(如园区/北门)下面所有设备都拿出来"用本工具更直接。
            注意:deviceTypeList 为空时会返回全部类型的设备,返回体可能很大。
            """
    )
    public String listDeviceWithSpace(
            @McpToolParam(description = "设备类型编码数组,如 [\"0\",\"3\"] 表示门禁和监控;不传返回全部类型", required = false) String[] deviceTypeList,
            @McpToolParam(description = "设备子类型,精确匹配,一般不用传", required = false) String deviceType,
            @McpToolParam(description = "启用状态,精确匹配。0停用 1启用 2维修 3报废", required = false) String status,
            @McpToolParam(description = "空间位置ID,查该节点下级的设备", required = false) String spaceId
    ) {
        Map<String, Object> body = cond(
                "deviceType", deviceType,
                "status", status,
                "spaceId", spaceId);
        if (deviceTypeList != null && deviceTypeList.length > 0) {
            body.put("syncSourceList", Arrays.asList(deviceTypeList));
        }
        Object result = platformApiCallServiceImpl.doPostCall(base(DEVICE_INFO_WITH_SPACE_URL), body);
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getDeviceDetail",
            description = TOOL_DESC_PREFIX + """
            按设备内部 id 查询单台设备的详情(设备资产记录)。
            id 从 listDevice / listDeviceOnly 返回的 id 字段取,不是设备编号 code。
            返回 data 为单个设备对象,比列表多几个关联查询出来的字段:
            - spaceName: 所在空间全路径名
            - personInChargeName: 负责人姓名
            - orgName: 所属组织名称
            但不会返回园区名和附件列表。
            设备不存在时查询会失败(平台返回 status=0 / msg="该设备不存在"),
            此时本工具返回的是一段提示文本,不是设备数据。
            另外本接口不做数据权限过滤,拿到 id 就能读出详情。
            请根据用户问题(如"这台设备的负责人是谁"/"设备在哪个位置"/"设备详情")传入 id 查询。
            """
    )
    public String getDeviceDetail(
            @McpToolParam(description = "设备内部id,从设备列表的 id 字段取") String id
    ) {
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(DEVICE_INFO_DETAIL_URL), "id", id));
        return orFail(result, "设备id不存在");
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getDeviceStatistics",
            description = TOOL_DESC_PREFIX + """
            按「单位 × 部门 × 设备类型」聚合统计设备数量,支持分页。
            返回 data 为 {page:{...}, data:[统计行数组]},每行含:
            - unit/department: 所属单位、管理部门名称
            - type: 设备类型中文名;deviceType: 类型编码
            - quantity: 设备数量;totalValue: 设备总价
            - enabled: 启用数量(status=1);disabled: 停用数量(status=0)
            筛选参数都模糊匹配、都可选:unit(单位名)、department(部门名)、type(类型中文名)。
            注意 enabled/disabled 是"启用/停用"口径,不是"在线/离线",别当在线率回答。
            请根据用户问题(如"各部门有多少设备"/"安保部的设备资产"/"哪类设备最多")从返回中提取信息回答。
            """
    )
    public String getDeviceStatistics(
            @McpToolParam(description = "所属单位名称,模糊匹配", required = false) String unit,
            @McpToolParam(description = "管理部门名称,模糊匹配", required = false) String department,
            @McpToolParam(description = "设备类型中文名,模糊匹配", required = false) String type,
            @McpToolParam(description = "页码,从1开始,可不传默认1", required = false) Integer pageCurrent,
            @McpToolParam(description = "每页条数,可不传默认100", required = false) Integer pageSize
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("page", page(pageCurrent, pageSize));
        // 平台侧 @RequestBody(required=false) 但 cond 为 null 时会 NPE,所以 body 必须传(空对象也要传)
        body.put("data", cond("unit", unit, "department", department, "type", type));
        Object result = platformApiCallServiceImpl.doPostCall(base(DEVICE_INFO_STATISTICS_URL), body);
        return JSON.toJSONString(result);
    }

    // ==================================================================
    // 二、设备状态与类型统计(/deviceInfo/*)
    // ==================================================================
    // 本组所有接口的 status 口径均为"设备启用状态"(VISUAL_DEVICE_STATUS:
    // 0停用 1启用 2维修 3报废),不是在线/离线。tools 描述里已逐条标明。

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "countByDeviceType",
            description = TOOL_DESC_PREFIX + """
            按设备类型统计设备数量(饼图/柱状图口径)。
            返回数组,每项含 count(数量)、deviceType(类型编码)、deviceTypeName(类型中文名)。
            字典里存在但当前没有设备的类型也会返回,count 为 0。
            spaceId 可选,传了就只统计该空间及其下级。
            请根据用户问题(如"各类设备各有多少台"/"监控设备有多少")从返回中提取信息回答。
            """
    )
    public String countByDeviceType(
            @McpToolParam(description = "空间位置ID,只统计该空间及下级;不传统计全园区", required = false) String spaceId
    ) {
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(TYPE_NUM_STATISTIC_URL), "spaceId", spaceId));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "countByStatus",
            description = TOOL_DESC_PREFIX + """
            按设备启用状态统计数量。
            返回数组,每项含 count(数量)、status(状态编码)、statusName(状态中文名)、deviceType。
            状态字典:0停用 1启用 2维修 3报废。字典里存在但数量为 0 的状态也会返回。
            参数都可选:spaceId(限定空间及下级)、deviceType(限定设备类型编码,0门禁 1道闸 2梯控 3监控
            4入侵报警 5广播 6水表 7电表)。
            注意这是"启用/停用"统计,不是在线率。用户问"多少设备停用/启用"时用它;
            问"多少设备离线/在线"要改用能耗或安防那几个在线率工具。
            """
    )
    public String countByStatus(
            @McpToolParam(description = "空间位置ID,限定该空间及下级", required = false) String spaceId,
            @McpToolParam(description = "设备类型编码。0门禁 1道闸 2梯控 3监控 4入侵报警 5广播 6水表 7电表", required = false) String deviceType
    ) {
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(STATUS_NUM_STATISTIC_URL), "spaceId", spaceId, "deviceType", deviceType));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "countByTypeAndStatus",
            description = TOOL_DESC_PREFIX + """
            按「设备类型 × 启用状态」交叉统计数量。
            返回数组,每项含 deviceType(类型编码)、deviceTypeName(类型中文名)、
            statusCount(状态编码 → 数量 的映射,如 {"0":3,"1":25})。
            状态编码:0停用 1启用 2维修 3报废。
            spaceId 可选,传了就只统计该空间及其下级。
            要"每一类设备里启用多少、停用多少"这种交叉口径时用它;
            只要单维度用 countByDeviceType 或 countByStatus。
            注意这是"启用/停用"口径,不是在线率。
            """
    )
    public String countByTypeAndStatus(
            @McpToolParam(description = "空间位置ID,只统计该空间及下级;不传统计全园区", required = false) String spaceId
    ) {
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(ALL_STATUS_NUM_STATISTIC_URL), "spaceId", spaceId));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "countBySpace",
            description = TOOL_DESC_PREFIX + """
            按空间位置统计设备数量。
            返回数组,每项含 spaceId、count(数量)。deviceType 可选,限定设备类型编码。
            两个必须知道的坑:
            - spaceName 字段平台侧 SQL 没查,恒为 null —— 拿不到空间中文名,不要编;
              要空间名请用 countByFloor 或 getEquipDistribution。
            - 本接口会排除"停用"设备(status=0),统计口径和其它统计接口不一致,
              不要拿它的数字和其它接口横向相加或对比。
            """
    )
    public String countBySpace(
            @McpToolParam(description = "设备类型编码。0门禁 1道闸 2梯控 3监控 4入侵报警 5广播 6水表 7电表", required = false) String deviceType
    ) {
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(SPACE_NUM_STATISTIC_URL), "deviceType", deviceType));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "countByFloor",
            description = TOOL_DESC_PREFIX + """
            按楼层统计设备数量。
            返回数组,每项含 space_name(楼层名称)、count(该楼层设备数)。
            没有设备的楼层也会返回,count 为 0 —— 所以总数不能被这些 0 拉低,
            算总数时把 count 相加即可(0 不影响)。
            deviceType 可选,限定设备类型编码。
            请根据用户问题(如"每层楼有多少设备"/"哪层设备最多")从返回中提取信息回答。
            """
    )
    public String countByFloor(
            @McpToolParam(description = "设备类型编码。0门禁 1道闸 2梯控 3监控 4入侵报警 5广播 6水表 7电表", required = false) String deviceType
    ) {
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(FLOOR_NUM_STATISTIC_URL), "deviceType", deviceType));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "countMonitorBySubType",
            description = TOOL_DESC_PREFIX + """
            统计监控设备的各子类型数量。固定只统计监控设备(类型编码 3),不用也不能指定设备类型。
            返回数组,每项含 subTypeCode(子类型编码)、subTypeName(子类型中文名)、count(数量)。
            spaceId 可选,限定该空间及其下级。
            请根据用户问题(如"监控里枪机球机各多少"/"摄像机子类型分布")从返回中提取信息回答。
            """
    )
    public String countMonitorBySubType(
            @McpToolParam(description = "空间位置ID,限定该空间及下级", required = false) String spaceId
    ) {
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(MONITOR_TYPE_STATISTIC_URL), "spaceId", spaceId));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "countMonitorByStatus",
            description = TOOL_DESC_PREFIX + """
            统计监控设备的各启用状态数量。固定只统计监控设备(类型编码 3)。
            返回数组,每项含 statusCode(状态编码)、statusName(状态中文名)、count(数量)。
            状态编码:0停用 1启用 2维修 3报废。
            spaceId 可选,限定该空间及其下级。
            注意这是"启用/停用"口径,不是监控的在线率。要监控实时在线率请用 getCameraOnlinePercentage。
            """
    )
    public String countMonitorByStatus(
            @McpToolParam(description = "空间位置ID,限定该空间及下级", required = false) String spaceId
    ) {
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(MONITOR_STATUS_STATISTIC_URL), "spaceId", spaceId));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getDistribution",
            description = TOOL_DESC_PREFIX + """
            按「单位 × 部门 × 设备类型」统计设备分布,带数量排名。
            返回数组,每项含 unit(单位)、department(部门)、type(设备类型中文名)、
            quantity(数量)、totalValue(总价)、paiming(按数量倒序的排名)。
            筛选参数都模糊匹配、都可选:unit(单位名)、department(部门名)、type(类型中文名)。
            本接口无分页,一次返回全部统计行。
            与 getDeviceStatistics 的区别:这个带排名、不分页,适合"排名/占比"类问题;
            那个带分页,适合"逐行看"的清单。
            请根据用户问题(如"哪个部门设备最多"/"设备数量排名")从返回中提取信息回答。
            """
    )
    public String getDistribution(
            @McpToolParam(description = "所属单位名称,模糊匹配", required = false) String unit,
            @McpToolParam(description = "管理部门名称,模糊匹配", required = false) String department,
            @McpToolParam(description = "设备类型中文名,模糊匹配", required = false) String type
    ) {
        Object result = platformApiCallServiceImpl.doPostCall(
                base(DISTRIBUTION_URL), cond("unit", unit, "department", department, "type", type));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "countByParam",
            description = TOOL_DESC_PREFIX + """
            按设备类型分类统计数量,同时给出启用/停用数。
            返回 data 含 total(设备总数)、online(启用数)、offline(停用数),
            以及 details 数组,每项含 deviceType(类型中文名,不是编码)、total、online、offline。
            筛选参数都模糊匹配、都可选:unit(单位名)、department(部门名)、type(类型中文名)。
            必须注意的命名陷阱:这里的 online / offline 字段名有严重误导,
            实际来源是设备 status 字段的"启用"(status=1)和"停用"(status=0),
            与设备在线率毫无关系。回答时务必说成"启用/停用",不要按字面说成"在线/离线"。
            """
    )
    public String countByParam(
            @McpToolParam(description = "所属单位名称,模糊匹配", required = false) String unit,
            @McpToolParam(description = "管理部门名称,模糊匹配", required = false) String department,
            @McpToolParam(description = "设备类型中文名,模糊匹配", required = false) String type
    ) {
        Object result = platformApiCallServiceImpl.doPostCall(
                base(QUERY_DEVICE_BY_PARAM_URL), cond("unit", unit, "department", department, "type", type));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getEquipClass",
            description = TOOL_DESC_PREFIX + """
            查询设备分类饼图数据。返回数组,每项含:
            - name: 设备类别名称(如消防/空调/能耗/门禁等)
            - value: 该类别设备数量
            - itemStyle.color: 该类别的展示颜色
            全部类别的 value 合计即设备总数。
            与 countByDeviceType 的区别:本接口按"类别"(消防/空调/能耗这种业务分类)汇总,
            且带图表颜色;countByDeviceType 按 syncSource 设备类型编码(门禁/道闸/监控这种)汇总。
            两者维度不同、数字不可互相印证,更不要相加。
            请根据用户问题(如"园区有多少设备"/"哪类设备最多"/"各类设备占比")从返回中提取信息回答。
            """
    )
    public String getEquipClass() {
        Object result = platformApiCallServiceImpl.doGetCall(base(EQUIP_CLASS_URL));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getStatisRegion",
            description = TOOL_DESC_PREFIX + """
            查询各区域设备数量统计(柱状图数据)。返回:
            - xAxisNameMap: 区域名称数组
            - VALUE: 与区域对应的设备数量数组
            - MAX: 与区域对应的最大值数组(用于图表刻度,不是真实数据,不要当设备数回答)
            deviceType 可选,按设备类型编码过滤(0门禁 1道闸 2梯控 3监控 4入侵报警 5广播 6水表 7电表)。
            与 countBySpace 的区别:本接口返回区域名称,countBySpace 的 spaceName 恒为 null,
            所以问"哪个区域设备最多"要用本工具。
            请根据用户问题(如"哪个区域设备最多"/"各区域设备分布")从返回中提取信息回答。
            """
    )
    public String getStatisRegion(
            @McpToolParam(description = "设备类型编码,可不传统计全部类型。0门禁 1道闸 2梯控 3监控 4入侵报警 5广播 6水表 7电表", required = false) String deviceType
    ) {
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(MONITOR_STATIS_REGION_URL), "deviceType", deviceType));
        return JSON.toJSONString(result);
    }

    // ==================================================================
    // 三、设备在线率
    // ==================================================================
    // 本组各接口的在线判定口径差异很大,描述里逐个标明,不要混用结论。

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getEnergyOnlineRate",
            description = TOOL_DESC_PREFIX + """
            查询能耗设备(电表/水表)的在线率。这是平台内唯一基于独立在线状态表的真实在线数据。
            必传 syncSource:7-电表,6-水表(只支持这两个,其它值会查出无意义的 0/0)。
            返回 data 含 total(设备总数)、ONLINE_PERCENTAGE(在线率)、online_count(在线数)。
            两个必须注意的点:
            - ONLINE_PERCENTAGE 是 0~1 的小数(如 0.92 表示 92%),不是百分数。
              回答用户时要换算成百分比,比如 0.92 说成 92%。
            - 分母只含"启用"状态的设备(status=1),停用设备不计入。
            请根据用户问题(如"电表在线率多少"/"有多少水表离线")从返回中提取信息回答。
            """
    )
    public String getEnergyOnlineRate(
            @McpToolParam(description = "设备类型,必传。7-电表 / 6-水表") String syncSource
    ) {
        EnergySyncSource source = EnergySyncSource.fromCode(syncSource);
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(ENERGY_ONLINE_RATE_URL), "syncSource", source.getCode()));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getEnergyDeviceStatus",
            description = TOOL_DESC_PREFIX + """
            查询能耗设备(电表 + 水表合并)的在线状态汇总,无参数。
            返回 data 含 onlineCount(在线数)、offlineCount(离线数)、totalCount(总数)。
            这是真实在线数据(来自能耗在线表)。
            请根据用户问题(如"能耗设备在线情况"/"有多少表具离线")从返回中提取信息回答。
            """
    )
    public String getEnergyDeviceStatus() {
        Object result = platformApiCallServiceImpl.doGetCall(base(ENERGY_DEVICE_STATUS_URL));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getCategoryHealth",
            description = TOOL_DESC_PREFIX + """
            查询各类别设备的健康度,无参数。返回 data.allDevices 数组,每项含:
            - name: 类别名(安防设备/消防设备/空调设备/能耗设备)
            - score: 综合健康评分(0-100)
            - onlineRate: {current(在线数), total(总数), percent(百分比整数 0-100)}
            - maintenanceRate: 维修完成率(暂为 0,未接工单表)
            - lifeRate: 设备寿命率,结构同 onlineRate
            口径警告:各类别的 onlineRate 判定依据不一致 —— 能耗设备用的是真实在线表,
            安防设备用的是 status='1'(启用状态)近似,不是真实在线。回答安防在线率时要说清这点。
            消防、空调两类目前没有独立设备类型数据,返回空对象,不要说成"健康度为0"。
            与 device:getCategoryHealth 查的是同一个接口,结果完全一样,任选其一即可 ——
            不要两个都调,更不要把两次结果当两批设备相加。
            请根据用户问题(如"各类设备健康度"/"哪类设备状态最差")从返回中提取信息回答。
            """
    )
    public String getCategoryHealth() {
        Object result = platformApiCallServiceImpl.doGetCall(base(CATEGORY_HEALTH_URL));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getEquipDistribution",
            description = TOOL_DESC_PREFIX + """
            查询各楼层的设备分布。
            返回 data 含 xData(楼层名称数组)、yData1(与 xData 对应的"在线"数)、yData2(与 xData 对应的"离线"数)。
            命名陷阱:yData1/yData2 虽然叫在线/离线,实际判定依据是设备 status 字段的
            "启用"(status=1)和"停用"(status=0),不是真实在线状态。
            回答时必须说成"启用/停用",不要按字段名字面说成"在线/离线"。
            type 可选,按分布类型筛选。
            """
    )
    public String getEquipDistribution(
            @McpToolParam(description = "分布类型筛选,可不传", required = false) String type
    ) {
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(EQUIP_DISTRIBUTION_URL), "type", type));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getCameraOnlinePercentage",
            description = TOOL_DESC_PREFIX + """
            查询监控设备的实时在线率(实时调厂商平台,基于厂商返回的 isOnline 字段计算)。这是真实在线数据。
            返回 data 含 onlineNum(在线数)、totalNum(总数)、onlinePercentage(在线率百分比,保留0位小数)。
            必须知道的局限:厂商接口不支持分页,平台只拿"当前页"数据计算,
            所以 totalNum 是当前页条数而不是全量设备数。本工具默认把每页撑到1000条来尽量拿全,
            但如果监控设备超过1000个,返回的在线率会失真 —— 回答时可以说明"基于最近一次拉取的分页数据"。
            厂商侧监控列表为空时接口会失败,本工具会返回提示文本而不是数据。
            请根据用户问题(如"监控在线率多少"/"有多少摄像头离线")从返回中提取信息回答。
            """
    )
    public String getCameraOnlinePercentage() {
        Object result = platformApiCallServiceImpl.doPostCall(
                base(CAMERA_ONLINE_PERCENTAGE_URL), vendorAnfangBody());
        return orFail(result, "厂商侧监控列表为空");
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getMjOnlinePercentage",
            description = TOOL_DESC_PREFIX + """
            查询门禁设备的实时在线率(实时调厂商平台,基于 isOnline 计算)。这是真实在线数据。
            返回 data 含 onlineNum(在线数)、totalNum(总数)、onlinePercentage(在线率百分比,保留0位小数)。
            与 getCameraOnlinePercentage 同样的局限:厂商接口不支持分页,只统计"当前页",
            totalNum 是当前页条数;本工具默认把每页撑到1000条尽量拿全,超过1000个门禁时会失真。
            与 device:getMjOnlinePercentage 查的是同一个接口(请求参数也一样),结果完全一样,
            任选其一即可,不要两个都调。
            请根据用户问题(如"门禁在线率多少"/"有多少门禁离线")从返回中提取信息回答。
            """
    )
    public String getMjOnlinePercentage() {
        Object result = platformApiCallServiceImpl.doPostCall(
                base(MJ_ONLINE_PERCENTAGE_URL), vendorAnfangBody());
        return orFail(result, "厂商侧门禁列表为空");
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getAnfangDeviceOnlinePercentage",
            description = TOOL_DESC_PREFIX + """
            查询安防设备(周界 + 门禁)的实时在线率,实时调厂商平台。这是真实在线数据。
            返回 data 含两组,每组都有 totalNum(总数)、onlineNum(在线数)、onlinePercentage(在线率百分比):
            - zhoujie: 周界
            - menjin: 门禁(与 getMjOnlinePercentage 是同一批设备的两种口径,注意别重复计数)
            两个注意点:
            - 本接口的 onlinePercentage 保留2位小数,而 getCameraOnlinePercentage / getMjOnlinePercentage
              保留0位小数,精度不一致,横向对比时留意。
            - "周界"设备是靠通道名称里包含"周界"两个字筛出来的,依赖设备命名规范,
              命名不规范的设备不会被算进周界。
            与 device:getAnfangDeviceOnlinePercentage 查的是同一个接口,结果完全一样,任选其一即可。
            另注意其中的 menjin 组与 getMjOnlinePercentage 是同一批门禁设备,
            要"安防总体"就用本工具的 zhoujie + menjin,不要再叠加 getMjOnlinePercentage 的结果。
            请根据用户问题(如"周界在线率"/"安防设备在线情况")从返回中提取信息回答。
            """
    )
    public String getAnfangDeviceOnlinePercentage() {
        Object result = platformApiCallServiceImpl.doPostCall(
                base(ANFANG_ONLINE_PERCENTAGE_URL), vendorAnfangBody());
        return orFail(result, "厂商侧安防设备列表为空");
    }

    // ==================================================================
    // 四、厂商平台实时设备列表(calldevice 透传)
    // ==================================================================

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getVendorDeviceList",
            description = TOOL_DESC_PREFIX + """
            实时透传厂商平台(大华 ICC / ITC / 和信)的设备列表,返回厂商原始 JSON,不查平台库。
            deviceType 必填,取值与对应厂商、返回结构:
            - jk   监控设备。大华 dahua/V5.0.16。data 是通道数组,每项含 channelCode(通道编码,如 1000000$1$0$0)、
                  channelName(通道名称)、cameraType(摄像机类型,1-枪机 2-球机 3-半球 5-本地采集)、
                  chExt.channelDeviceIp(设备IP);海康侧 data 为 {total, list[]},list 每项含 cameraIndexCode/
                  cameraName/cameraType/status(0-离线 1-在线)。
            - mj   门禁设备。大华 dahua/V5.0.16。data 为 {totalRows(总记录数), pageData[]},pageData 每项含
                  deviceCode(设备编码)、deviceName(设备名称)、deviceIp(设备IP)、units[](门禁通道在 unitType=7 的
                  单元下,通道含 channelCode/channelName)。
            - dz   道闸设备。大华 dahua/V5.0.16。结构同 mj。5 个类型里只有本类型支持 otherparam 过滤。
            - gb   广播设备。ITC itc/V1.0。data.EndPointsArray[],每项含 EndpointID(终端编号)、
                  EndpointName(终端名称)、EndpointIP(终端IP)。
            - xxfb 信息发布设备。和信 hx/V4.8.5。data 是设备数组,每项含 code(设备编号)、name(设备名称)、
                  ipAddress(IP)、address(安装地址)、online(0-离线 1-在线)、status(0-未激活 1-占用 3-停用 4-异常)、
                  lastTime(最后在线时间)、deptName(部门)、resolution(分辨率) 等。
            什么时候用本工具、什么时候用 listDevice:
            - 本工具查的是厂商平台实时数据,不落库,用来核对"厂商那边现在有什么设备"、
              拿实时在线状态(只有这里的 status/online 才是厂商侧的真实状态)。
            - listDevice 查的是平台已纳管的资产库,是"平台认的设备台账"。设备清单、资产数量、负责人、
              所属部门这些一律用 listDevice,不要用本工具。
            使用注意:
            - 上面只列了各厂商的常用字段,返回里还有更多字段,需要时可以直接读。
            - 分页由厂商平台处理:mj/dz 的总数看 data.totalRows,jk 海康侧看 data.total,大华侧和 gb/xxfb 没有
              总数。条数不够时靠 pageNum 翻页,不要一次要几千条。
            - xxfb 忽略全部入参(含分页),每次都是全量返回。
            - 返回可能很长:先看总数再决定要不要翻页,答复用户时只摘要,不要把整页原始数据念出来。
            请根据用户问题(如"厂商那边有哪些监控设备"/"广播终端都有哪些"/"道闸设备列表")
            选对应的 deviceType 查询,并从返回中提取对应信息回答。
            """
    )
    public String getVendorDeviceList(
            @McpToolParam(description = "设备类型,必填。jk(监控设备) / mj(门禁设备) / dz(道闸设备) / gb(广播设备) / xxfb(信息发布设备)") String deviceType,
            @McpToolParam(description = "页码,字符串,从1开始,可不传默认1", required = false) String pageNum,
            @McpToolParam(description = "每页条数,字符串,可不传默认1000，如果你想查的设备可能超过1000个，那就传更大的值", required = false) String pageSize,
            @McpToolParam(description = "附加过滤参数,仅 deviceType=dz(道闸)生效,其他类型传了会被忽略", required = false) String otherparam
    ) {
        VendorDeviceType type = VendorDeviceType.fromCode(deviceType);
        Object result = platformApiCallServiceImpl.doPostCall(
                base(type.getUrl()), buildVendorBody(type, pageNum, pageSize, otherparam));
        return JSON.toJSONString(result);
    }

    @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getVendorDeviceDetail",
            description = TOOL_DESC_PREFIX + """
            按设备编号查询单个设备的详情(平台的设备资产记录)。
            code 必填,是平台侧的设备资产编号,形如 CY-HIK-JK-001-0001 ——
            不是 getVendorDeviceList 返回里厂商侧的通道编码(channelCode)/终端编号(EndpointID),两者不是一回事;
            也不是 getDeviceDetail 要的设备内部 id。
            返回 data 是设备资产对象,含设备自身属性(名称/类型/状态/IP 等)和嵌套的 space 空间对象
            (设备所在的空间位置信息)。
            读返回时注意:
            - 返回里没有出现的字段,是接口没有返回,不等于"设备没有这个属性"。
              不要说"这台设备没有负责人/不在任何组织下",要说"这个接口没返回该信息";
              需要负责人、所属组织、空间全路径名这几个关联字段时改用 getDeviceDetail(按 id 查)。
            - 本接口不按删除标志过滤,已删除的设备也可能被查出来。
            - 设备不存在时查询会失败(平台返回 status=0 / msg="该设备不存在"),
              此时本工具返回的是一段提示文本,不是设备数据。
            请根据用户问题(如"查一下 CY-HIK-JK-001-0001 这台设备")传入对应的设备编号查询。
            """
    )
    public String getVendorDeviceDetail(
            @McpToolParam(description = "设备编号,必填,形如 CY-HIK-JK-001-0001") String code
    ) {
        Object result = platformApiCallServiceImpl.doGetCall(
                withQuery(base(DEVICE_DETAIL_BY_CODE_URL), "code", code));
        return orFail(result, "设备编号不存在");
    }

    // ==================================================================
    // 内部工具方法
    // ==================================================================

    /** 拼 baseUrl + 接口路径 */
    private String base(String path) {
        return platformApiCallServiceImpl.getBaseUrl() + path;
    }

    /**
     * 拼 GET 的 query string:键值成对传入,值为 null 或空白则跳过,值做 URL 编码。
     * 跳过空值不是为了好看 —— 平台侧不少条件是精确匹配,传空串会变成"什么都不匹配"。
     */
    private static String withQuery(String url, String... keyValues) {
        StringBuilder sb = new StringBuilder(url);
        char sep = '?';
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String key = keyValues[i];
            String value = keyValues[i + 1];
            if (value == null || value.isBlank()) {
                continue;
            }
            sb.append(sep).append(key).append('=').append(encode(value.trim()));
            sep = '&';
        }
        return sb.toString();
    }

    /**
     * 组装查询条件对象,跳过 null 和空白值。
     * 平台侧 name/unit/type 等是模糊匹配,传空串会退化成"匹配全部";
     * code/syncSource/status 等是精确匹配,传空串会变成"什么都匹配不到"。跳过最安全。
     */
    private static Map<String, Object> cond(String... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String value = keyValues[i + 1];
            if (value == null || value.isBlank()) {
                continue;
            }
            map.put(keyValues[i], value.trim());
        }
        return map;
    }

    /** 组装 deviceInfo 系列的分页对象,空值取默认 */
    private static Map<String, Object> page(Integer current, Integer size) {
        Map<String, Object> page = new HashMap<>();
        page.put("current", (current == null || current <= 0) ? DEFAULT_PAGE_CURRENT : current);
        page.put("size", (size == null || size <= 0) ? DEFAULT_PAGE_SIZE : size);
        return page;
    }

    /**
     * 组装厂商列表接口请求体:厂商编码/协议版本取自设备类型,分页参数字符串透传。
     * otherparam 只有道闸接口生效,其他类型不拼这个字段。
     */
    private static Map<String, Object> buildVendorBody(
            VendorDeviceType type, String pageNum, String pageSize, String otherparam) {
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", type.getProducerCode());
        body.put("protocolVersion", type.getProtocolVersion());
        body.put("pageNum", blankToDefault(pageNum, DEFAULT_VENDOR_PAGE_NUM));
        body.put("pageSize", blankToDefault(pageSize, DEFAULT_VENDOR_PAGE_SIZE));
        if (type == VendorDeviceType.DZ && otherparam != null && !otherparam.isBlank()) {
            body.put("otherparam", otherparam);
        }
        return body;
    }

    /**
     * 安防在线率接口(dahua/V5.0.16)请求体。
     * pageSize 给大值是有意的:这几个接口只统计"当前页",页越大算出的在线率越接近真实。
     */
    private static Map<String, Object> vendorAnfangBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", DAHUA_PRODUCER_CODE);
        body.put("protocolVersion", DAHUA_PROTOCOL_VERSION);
        body.put("pageNum", DEFAULT_VENDOR_PAGE_NUM);
        body.put("pageSize", ONLINE_RATE_PAGE_SIZE);
        return body;
    }

    /** 空值兜底:入参为空/空白字符串时用默认值,否则去掉首尾空格 */
    private static String blankToDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    /**
     * doGetCall / doPostCall 只返回信封里的 data,平台侧的 status/msg 在这一层已经丢了,
     * 查询失败(如"该设备不存在")到这里就是 null。直接吐 "null" 模型没法解释,所以兜一句人话。
     */
    private static String orFail(Object result, String likelyCause) {
        if (result == null) {
            return "查询失败:平台未返回数据(data 为空)。可能是" + likelyCause
                    + ",也可能是平台侧返回了失败状态(本工具拿不到平台的 status/msg,区分不了)。";
        }
        return JSON.toJSONString(result);
    }

    /** URL 参数编码,避免中文或特殊字符破坏 query string;入参为 null 时返回空串 */
    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    // ==================================================================
    // 词表
    // ==================================================================

    /**
     * 能耗在线率的设备类型词表。
     *
     * <p>只有水表/电表有独立在线表,传成别的类型会查出 0/0 的假在线率,
     * 所以这里挡掉而不是透传 —— 让模型看到报错比让它答出"在线率 0%"好。</p>
     */
    public enum EnergySyncSource {

        /** 水表 */
        WATER_METER("6", "水表"),
        /** 电表 */
        ELECTRIC_METER("7", "电表");

        private final String code;
        private final String label;

        EnergySyncSource(String code, String label) {
            this.code = code;
            this.label = label;
        }

        /**
         * 解析设备类型编码,大小写与首尾空格不敏感;不在词表内时抛异常并列出可选值。
         *
         * @param code 工具入参 syncSource
         * @return 对应的能耗设备类型
         * @throws IllegalArgumentException 取值不在词表内
         */
        public static EnergySyncSource fromCode(String code) {
            String normalized = code == null ? "" : code.trim().toLowerCase();
            for (EnergySyncSource source : values()) {
                if (source.code.equals(normalized)) {
                    return source;
                }
            }
            throw new IllegalArgumentException(
                    "不支持的 syncSource: " + code + ",可选值: " + hint());
        }

        /** 全部可选值的提示串,格式 {@code 7(电表) / 6(水表)} */
        public static String hint() {
            return Arrays.stream(values())
                    .map(source -> source.code + "(" + source.label + ")")
                    .collect(Collectors.joining(" / "));
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 厂商设备类型词表:设备类型编码 → 接口 URL + 三方协议入参 + 中文名。
     *
     * <p>5 个类型的入参完全一致,差异只有"调哪个 URL、用哪个厂商"这两样,所以收在一处。
     * 新增设备类型时只在这里加一个枚举值,{@link #getVendorDeviceList} 与
     * {@link #buildVendorBody} 都不用改。</p>
     */
    public enum VendorDeviceType {

        /** 监控设备,大华安防协议 */
        JK("jk", JK_DEVICE_LIST_URL, DAHUA_PRODUCER_CODE, DAHUA_PROTOCOL_VERSION, "监控设备"),
        /** 门禁设备,大华安防协议 */
        MJ("mj", MJ_DEVICE_LIST_URL, DAHUA_PRODUCER_CODE, DAHUA_PROTOCOL_VERSION, "门禁设备"),
        /** 道闸设备,大华安防协议;5 个类型里唯一支持 otherparam */
        DZ("dz", DZ_DEVICE_LIST_URL, DAHUA_PRODUCER_CODE, DAHUA_PROTOCOL_VERSION, "道闸设备"),
        /** 广播设备,ITC 协议 */
        GB("gb", GB_DEVICE_LIST_URL, ITC_PRODUCER_CODE, ITC_PROTOCOL_VERSION, "广播设备"),
        /** 信息发布设备,和信协议 */
        XXFB("xxfb", XXFB_DEVICE_LIST_URL, HX_PRODUCER_CODE, HX_PROTOCOL_VERSION, "信息发布设备");

        /** 工具入参 deviceType 的取值 */
        private final String code;
        /** 该类型的设备列表接口 URL(不含 baseUrl) */
        private final String url;
        /** 三方协议必传参数:调用方标识 */
        private final String producerCode;
        /** 三方协议必传参数:协议版本号 */
        private final String protocolVersion;
        /** 中文名,用于提示信息 */
        private final String label;

        VendorDeviceType(String code, String url, String producerCode, String protocolVersion, String label) {
            this.code = code;
            this.url = url;
            this.producerCode = producerCode;
            this.protocolVersion = protocolVersion;
            this.label = label;
        }

        /**
         * 按工具入参解析设备类型,大小写与首尾空格不敏感。
         *
         * <p>解析不出来时直接抛异常(而不是兜底成监控),避免模型填错类型时静默返回错的数据;
         * 异常信息里带上全部可选值,模型看到后可以自行纠正重试。</p>
         *
         * @param code 工具入参 deviceType
         * @return 对应的设备类型
         * @throws IllegalArgumentException 取值不在词表内
         */
        public static VendorDeviceType fromCode(String code) {
            String normalized = code == null ? "" : code.trim().toLowerCase();
            for (VendorDeviceType type : values()) {
                if (type.code.equals(normalized)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("不支持的 deviceType: " + code + ",可选值: " + hint());
        }

        /**
         * 全部可选值的提示串,格式 {@code jk(监控设备) / mj(门禁设备) / ...}。
         * 与 {@link #getVendorDeviceList} 的 deviceType 参数描述保持一致。
         */
        public static String hint() {
            return Arrays.stream(values())
                    .map(type -> type.code + "(" + type.label + ")")
                    .collect(Collectors.joining(" / "));
        }

        public String getCode() {
            return code;
        }

        public String getUrl() {
            return url;
        }

        public String getProducerCode() {
            return producerCode;
        }

        public String getProtocolVersion() {
            return protocolVersion;
        }

        public String getLabel() {
            return label;
        }
    }
}
