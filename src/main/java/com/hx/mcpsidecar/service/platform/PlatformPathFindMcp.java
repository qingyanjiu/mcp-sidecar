package com.hx.mcpsidecar.service.platform;

import com.alibaba.fastjson.JSON;
import com.hx.mcpsidecar.service.IApiCallService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 路径规划mcp工具集 - 来自platform平台
 */
@Service
public class PlatformPathFindMcp {

    private static final String TOOL_NAME_PREFIX = "path_find_service:";
    private static final String TOOL_DESC_PREFIX = "路径规划类服务:";

    // 查询路径规划配置列表接口URL
    public static final String PATH_FIND_GRID_LIST_URL = "/visualPathFindGrid/listOnly";

    @Autowired
    @Qualifier("platformApiCallServiceImpl")
    private IApiCallService platformApiCallServiceImpl;

    @McpTool(
        name = TOOL_NAME_PREFIX + "getPathFindGridList",
        description = TOOL_DESC_PREFIX + """
            查询路径规划配置列表,返回值是一个二维数据，代表空间内墙体的矩阵；1代表可以通过（是道路），0代表不能通过（是墙）
            """
    )
    public String getPathFindGridList() {
        String url = platformApiCallServiceImpl.getBaseUrl() + PATH_FIND_GRID_LIST_URL;
        Object result = platformApiCallServiceImpl.doGetCall(url);
        return JSON.toJSONString(result);
    }
}
