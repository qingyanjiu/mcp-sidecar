package com.hx.mcpsidecar.service;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class WeatherMcpService {

    @McpTool(description = "Get weather info for city")
    public String getWeather(
        @McpToolParam(description = "要查询天气的城市名称") String city,
        @McpToolParam(description = "查询日期，格式为YYYY-MM-DD") String date
    ) {
        return "大暴雨";
    }
}
