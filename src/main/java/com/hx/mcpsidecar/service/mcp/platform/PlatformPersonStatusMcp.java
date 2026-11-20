package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson2.JSON;
import com.hx.mcpsidecar.mock.MockData;
import com.hx.mcpsidecar.service.IApiCallService;
import com.hx.mcpsidecar.utils.UrlUtils;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
/**
 * 人员态势mcp工具集 - 来自platform平台
 */
@Service
public class PlatformPersonStatusMcp {

    private static final String TOOL_NAME_PREFIX = "person_status:";
    private static final String TOOL_DESC_PREFIX = "人员态势服务:";

    // 人员态势今日态势接口URL
    public static final String TODAY_PERSONNEL_AFFAIRS_URL = "/overview/getTodayPeople";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getTodayPersonnelAffairs",
            description = TOOL_DESC_PREFIX + """
                        查询今日人员态势。返回一句中文摘要：
                        实时在园人数：X人；今日进入人数：Y人；今日离开人数：Z人。
                        请根据用户问题只回答相关项：
                        - 用户问"进来了多少人/入园人数" → 只回答今日进入人数
                        - 用户问"离开了多少人/出园人数" → 只回答今日离开人数
                        - 用户问"现在在园多少人" → 只回答实时在园人数
                        - 用户没明确指定时，再返回全部三项
                        """
    )
    public String getTodayPersonnelAffairs() {
        String url = platformApiCallServiceImpl.getBaseUrl() + TODAY_PERSONNEL_AFFAIRS_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);

        // 平台响应已由 RestTemplate/Jackson 解析为 Map，这里直接取值，避免再次反序列化
        Map<?, ?> data = result instanceof Map<?, ?> map ? map : Collections.emptyMap();
        Integer realTimeNum = toInteger(data.get("realTimeNum"));
        Integer jingNum = toInteger(data.get("jingNum"));
        Integer chuNum = toInteger(data.get("chuNum"));

        // 默认值处理
        if (realTimeNum == null) realTimeNum = 0;
        if (jingNum == null) jingNum = 0;
        if (chuNum == null) chuNum = 0;

        return String.format(
                "实时在园人数：%d人；今日进入人数：%d人；今日离开人数：%d人。",
                realTimeNum, jingNum, chuNum
        );
    }

    /** 平台字段可能是 Number 或字符串，统一转成 Integer；缺失/非法时返回 null。 */
    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    // 人员态势人员流动 URL
    public static final String  PERSONNEL_FLOW_URL = "/personnel/personFlow";

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getTodayPersonnelFlow",
            description = TOOL_DESC_PREFIX + """
    查询人员流动数据,返回:
    - category: X轴时间标签
    - data.oneData: 进入(入园)人数序列,与 category 一一对应
    - data.twoData: 离开(出园)人数序列,与 category 一一对应
    时间粒度由 type 决定:
    - day(默认): 今日 00:00~23:00 按小时, category 为 24 个小时标签
    - week: 近 7 天按天, category 为 7 个日期
    - month: 近 30 天按天, category 为 30 个日期
    回答时必须明确说明哪组是进入人数、哪组是离开人数,禁止用"第一组/第二组"之类用户看不懂的代称。
    请根据用户问题(如"今天人员进出情况"/"本周进入人数趋势")从返回中提取对应信息回答。
    """
    )
    public String getPersonnelFlow(
            @McpToolParam(description = "时间粒度:day=今日按小时,week=近7天按天,month=近30天按天,默认day") String type
    ) {
        String flowType = (type == null || type.isBlank()) ? "day" : type;
        String url = UrlUtils.appendQueryParam(
                platformApiCallServiceImpl.getBaseUrl() + PERSONNEL_FLOW_URL, "type", flowType);
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }
    // 人员态势人员结构 URL
    public static final String  PERSONNEL_STRUCTURE_URL = "/personnel/personnelStructure";

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getPersonnelStructure",
            description = TOOL_DESC_PREFIX + """
    查询人员结构数据,返回值应该是公司名：人数
    """
    )
    public String getPersonnelStructure(String type) {
        String url = platformApiCallServiceImpl.getBaseUrl() + PERSONNEL_STRUCTURE_URL;

        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }
}
