package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.mock.MockData;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson.JSONObject;
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

        // 解析后端返回的 JSON
        JSONObject json = JSON.parseObject(JSON.toJSONString(result));
        Integer realTimeNum = json.getInteger("realTimeNum");
        Integer jingNum = json.getInteger("jingNum");
        Integer chuNum = json.getInteger("chuNum");

        // 默认值处理
        if (realTimeNum == null) realTimeNum = 0;
        if (jingNum == null) jingNum = 0;
        if (chuNum == null) chuNum = 0;

        return String.format(
                "实时在园人数：%d人；今日进入人数：%d人；今日离开人数：%d人。",
                realTimeNum, jingNum, chuNum
        );
    }
    // 人员态势人员流动 URL
    public static final String  PERSONNEL_FLOW_URL = "/personnel/personFlow";

    // @MockData
    @McpTool(
            name = TOOL_NAME_PREFIX + "getTodayPersonnelFlow",
            description = TOOL_DESC_PREFIX + """
    查询人员流动数据,返回值包含X轴分类标签以及进入/离开人数序列;type参数可选day/week/month,默认day
    """
    )
    public String getPersonnelFlow(String type) {
        String url = platformApiCallServiceImpl.getBaseUrl() + PERSONNEL_FLOW_URL + "?type=" + (type == null ? "day" : type);
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
