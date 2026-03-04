package com.hx.mcpsidecar.service.mcp.utils;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class TimeMcp {

    private static final String TOOL_NAME_PREFIX = "time_service:";
    private static final String TOOL_DESC_PREFIX = "时间类服务:";

    @McpTool(
        name = TOOL_NAME_PREFIX + "getCurrentDate",
        description = TOOL_DESC_PREFIX + """
            获取当前日期
            """
    )
    public String getCurrentDate() {
        return LocalDate.now().toString();
    }

}
