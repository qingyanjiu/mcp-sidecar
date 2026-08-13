package com.hx.mcpsidecar.service.mcp.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 安防态势mcp工具集 - 来自platform平台
 */
@Service
public class PlatformSecurityStatusMcp {

    private static final String TOOL_NAME_PREFIX = "security:";
    private static final String TOOL_DESC_PREFIX = "安防态势服务:";

    // 查询安防告警列表接口URL
    public static final String SECURITY_ALARM_LIST_URL = "/calldevice/alarm/anfang/alarmListWithType";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @McpTool(
            name = TOOL_NAME_PREFIX + "getSecurityAlarmList",
            description = TOOL_DESC_PREFIX + """
            查询安防告警列表,
            """
    )
    public String getSecurityAlarmList() {
        String url = platformApiCallServiceImpl.getBaseUrl() + SECURITY_ALARM_LIST_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }
}
