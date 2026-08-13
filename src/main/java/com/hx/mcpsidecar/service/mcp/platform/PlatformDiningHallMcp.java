package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 食堂管理mcp工具集 - 来自platform平台
 */
@Service
public class PlatformDiningHallMcp {

    private static final String TOOL_NAME_PREFIX = "canteen:";
    private static final String TOOL_DESC_PREFIX = "食堂管理服务:";

    // 查询每周食谱接口URL
    public static final String WEEKLY_RECIPE_URL = "/calldevice/dininghall/getWeeklyRecipe";
    // 接口必传参数:调用方标识(生产者编码)
    public static final String PRODUCER_CODE = "zhiye";
    // 接口必传参数:协议版本号
    public static final String PROTOCOL_VERSION = "V1.0";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @McpTool(
            name = TOOL_NAME_PREFIX + "getWeekMenu",
            description = TOOL_DESC_PREFIX + """
            查询食堂每周食谱,
            """
    )
    public String getWeeklyRecipe() {
        // 拼接接口地址及必传协议参数(producerCode / protocolVersion)
        String url = platformApiCallServiceImpl.getBaseUrl() + WEEKLY_RECIPE_URL
                + "?producerCode=" + PRODUCER_CODE
                + "&protocolVersion=" + PROTOCOL_VERSION;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }
}
