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
        name = TOOL_NAME_PREFIX + "getWeekOfDayOfSpecificDate",
        description = TOOL_DESC_PREFIX + """
            获取某一天是星期几
            """
    )
    public String getWeekOfDayOfSpecificDate(
        @McpToolParam(description = "要计算是星期几的具体日期，格式为 yyyy-MM-dd") String date
    ) {
        LocalDate localDate = LocalDate.parse(date);
        return localDate.getDayOfWeek().toString();
    }

    @McpTool(
        name = TOOL_NAME_PREFIX + "getCurrentDate",
        description = TOOL_DESC_PREFIX + """
            获取当前日期和星期
            """
    )
    public String getCurrentDate() {
        String date = LocalDate.now().toString();
        String week = LocalDate.now().getDayOfWeek().toString();
        return String.format("%s, %s", date, week);
    }

}
