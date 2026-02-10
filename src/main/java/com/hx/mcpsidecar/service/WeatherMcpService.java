package com.hx.mcpsidecar.service;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class WeatherMcpService {

    private static final String TOOL_NAME_PREFIX = "weather_service:";
    private static final String TOOL_DESC_PREFIX = "天气查询类服务:";

    @McpTool(
        name = TOOL_NAME_PREFIX + "getWeather",
        description = TOOL_DESC_PREFIX + """
            通过城市和日期查询当前的天气情况
            """
    )
    public String getWeather(
        @McpToolParam(description = "要查询天气的城市名称") String city,
        @McpToolParam(description = "查询日期，格式为YYYY-MM-DD") String date
    ) {
        return "大暴雨";
    }
}
