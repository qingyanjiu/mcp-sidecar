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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备查询mcp工具集 - 来自platform平台
 *
 * <p>两个工具:</p>
 * <ul>
 *   <li>{@code devicequery:getDeviceList} —— 对应 platform-call-device 模块的
 *       {@code /calldevice/{设备类型}/getDeviceList} 系列接口。5 个设备类型的入参完全一致,
 *       只有 URL 和厂商不同,因此收成一个工具,由 {@link DeviceType} 派发到对应厂商。</li>
 *   <li>{@code devicequery:getDeviceDetail} —— 对应设备资产模块的
 *       {@code /visual/getDeviceDetailByCode},按平台侧设备编号查单台设备详情。
 *       这条不在 calldevice 系列下,只是同属"设备查询"语义,所以挂在同一个端点里。</li>
 * </ul>
 *
 * <p>返回值是厂商/平台的原始 JSON,不做整形,字段随来源而异;分页由厂商平台处理,
 * 本模块不二次分页,所以成功响应里没有统一的 {@code {page,total}} 信封。</p>
 */
@Service
public class PlatformDeviceQueryMcp {

    private static final String TOOL_NAME_PREFIX = "device_query:";
    private static final String TOOL_DESC_PREFIX = "设备查询服务:";

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

    // 三方协议接口必传参数:大华(监控/门禁/道闸)
    public static final String DAHUA_PRODUCER_CODE = "dahua";
    public static final String DAHUA_PROTOCOL_VERSION = "V5.0.16";
    // 三方协议接口必传参数:ITC(广播)
    public static final String ITC_PRODUCER_CODE = "itc";
    public static final String ITC_PROTOCOL_VERSION = "V1.0";
    // 三方协议接口必传参数:和信(信息发布)
    public static final String HX_PRODUCER_CODE = "hx";
    public static final String HX_PROTOCOL_VERSION = "V4.8.5";

    // 分页参数按厂商约定是字符串(不是整数),透传给厂商平台
    private static final String DEFAULT_PAGE_NUM = "1";
    // 默认取1000条:一次覆盖绝大多数设备清单,省得为凑全量反复翻页
    private static final String DEFAULT_PAGE_SIZE = "1000";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @McpTool(
            name = TOOL_NAME_PREFIX + "getDeviceList",
            description = TOOL_DESC_PREFIX + """
            按设备类型实时查询厂商平台的设备列表,返回厂商原始 JSON(data 字段),字段随厂商而异。
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
            使用注意:
            - 上面只列了各厂商的常用字段,返回里还有更多字段,需要时可以直接读。
            - 分页由厂商平台处理:mj/dz 的总数看 data.totalRows,jk 海康侧看 data.total,大华侧和 gb/xxfb 没有
              总数。条数不够时靠 pageNum 翻页,不要一次要几千条。
            - xxfb 忽略全部入参(含分页),每次都是全量返回。
            - 返回可能很长:先看总数再决定要不要翻页,答复用户时只摘要,不要把整页原始数据念出来。
            请根据用户问题(如"有哪些监控设备"/"北门的门禁设备"/"道闸设备列表"/"广播终端都有哪些"/
            "信息发布设备在线情况")选对应的 deviceType 查询,并从返回中提取对应信息回答。
            """
    )
    public String getDeviceList(
            @McpToolParam(description = "设备类型,必填。jk(监控设备) / mj(门禁设备) / dz(道闸设备) / gb(广播设备) / xxfb(信息发布设备)") String deviceType,
            @McpToolParam(description = "页码,字符串,从1开始,可不传默认1", required = false) String pageNum,
            @McpToolParam(description = "每页条数,字符串,可不传默认1000，如果你想查的设备可能超过1000个，那就传更大的值", required = false) String pageSize,
            @McpToolParam(description = "附加过滤参数,仅 deviceType=dz(道闸)生效,其他类型传了会被忽略", required = false) String otherparam
    ) {
        DeviceType type = DeviceType.fromCode(deviceType);
        String url = platformApiCallServiceImpl.getBaseUrl() + type.getUrl();
        Object result = platformApiCallServiceImpl.doPostCall(url, buildBody(type, pageNum, pageSize, otherparam));
        return JSON.toJSONString(result);
    }

    @McpTool(
            name = TOOL_NAME_PREFIX + "getDeviceDetail",
            description = TOOL_DESC_PREFIX + """
            按设备编号查询单个设备的详情(平台的设备资产记录)。
            code 必填,是平台侧的设备资产编号,形如 CY-HIK-JK-001-0001 ——
            不是 getDeviceList 返回里厂商侧的通道编码(channelCode)/终端编号(EndpointID),两者不是一回事。
            返回 data 是设备资产对象,含设备自身属性(名称/类型/状态/IP 等)和嵌套的 space 空间对象
            (设备所在的空间位置信息)。
            读返回时注意:
            - spaceName、personInChargeName、orgName 这三个字段本接口恒为空,是接口本身就不返回,
              不代表"这台设备没有负责人/不在组织下"。用户问负责人或所属组织时,不要说"没有",
              要说"这个接口查不到"。
            - 本接口不按删除标志过滤,已删除的设备也可能被查出来。
            - 设备不存在时查询会失败(平台返回 status=0 / msg="该设备不存在"),
              此时本工具返回的是一段提示文本,不是设备数据。
            请根据用户问题(如"查一下 CY-HIK-JK-001-0001 这台设备"/"这个设备装在哪"/"设备详情")
            传入对应的设备编号查询,并从返回中提取对应信息回答。
            """
    )
    public String getDeviceDetail(
            @McpToolParam(description = "设备编号,必填,形如 CY-HIK-JK-001-0001") String code
    ) {
        String url = platformApiCallServiceImpl.getBaseUrl() + DEVICE_DETAIL_BY_CODE_URL + "?code=" + encode(code);
        Object result = platformApiCallServiceImpl.doGetCall(url);
        // doGetCall 只返回信封里的 data,平台侧的 status/msg 在这一层已经丢了,
        // 查询失败(如"该设备不存在")到这里就是 null。设备查不到是常见的一问,
        // 直接吐 "null" 模型没法解释,所以这里兜一句人话。
        if (result == null) {
            return "查询失败:平台未返回数据(data 为空)。可能是设备编号不存在,也可能是平台侧返回了失败状态"
                    + "(本工具拿不到平台的 status/msg,区分不了)。请先核对设备编号是否准确,"
                    + "形如 CY-HIK-JK-001-0001。";
        }
        return JSON.toJSONString(result);
    }

    /**
     * URL 参数编码,避免中文或特殊字符破坏 query string;入参为 null 时返回空串
     */
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

    /**
     * 组装请求体:厂商编码/协议版本取自设备类型,分页参数字符串透传。
     * otherparam 只有道闸接口生效,其他类型不拼这个字段。
     */
    private static Map<String, Object> buildBody(DeviceType type, String pageNum, String pageSize, String otherparam) {
        Map<String, Object> body = new HashMap<>();
        body.put("producerCode", type.getProducerCode());
        body.put("protocolVersion", type.getProtocolVersion());
        body.put("pageNum", blankToDefault(pageNum, DEFAULT_PAGE_NUM));
        body.put("pageSize", blankToDefault(pageSize, DEFAULT_PAGE_SIZE));
        if (type == DeviceType.DZ && otherparam != null && !otherparam.isBlank()) {
            body.put("otherparam", otherparam);
        }
        return body;
    }

    /**
     * 空值兜底:入参为空/空白字符串时用默认值,否则去掉首尾空格。
     */
    private static String blankToDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    /**
     * 设备类型词表:设备类型编码 → 接口 URL + 三方协议入参 + 中文名。
     *
     * <p>5 个类型的入参完全一致,差异只有"调哪个 URL、用哪个厂商"这两样,所以收在一处。
     * 新增设备类型时只在这里加一个枚举值,{@link #getDeviceList} 与 {@link #buildBody} 都不用改。</p>
     */
    public enum DeviceType {

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

        DeviceType(String code, String url, String producerCode, String protocolVersion, String label) {
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
        public static DeviceType fromCode(String code) {
            String normalized = code == null ? "" : code.trim().toLowerCase();
            for (DeviceType type : values()) {
                if (type.code.equals(normalized)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("不支持的 deviceType: " + code + ",可选值: " + hint());
        }

        /**
         * 全部可选值的提示串,格式 {@code jk(监控设备) / mj(门禁设备) / ...}。
         * 与 {@link #getDeviceList} 的 deviceType 参数描述保持一致。
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
