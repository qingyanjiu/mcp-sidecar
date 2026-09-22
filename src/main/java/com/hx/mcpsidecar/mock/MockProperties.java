package com.hx.mcpsidecar.mock;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * mock 开关配置,对应 yml 里的 {@code mcp.mock.*}。
 *
 * <p>开关按工具粒度生效,粒度由宽到细:</p>
 * <ol>
 *   <li>{@link #enabled} —— 总开关,决定默认行为</li>
 *   <li>{@link #include} / {@link #exclude} —— 白名单 / 黑名单</li>
 *   <li>{@link #tools} —— 按工具名精确覆盖,优先级最高</li>
 * </ol>
 *
 * <pre>{@code
 * mcp:
 *     mock:
 *         enabled: true
 *         include: []
 *         exclude: []
 *         tools:
 *             "device_query:getVendorDeviceList": false   # 含冒号,必须加引号
 * }</pre>
 *
 * <p>工具名取 {@code @McpTool} 的 {@code name} 属性,形如 {@code overview:getBasicInfo}。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "mcp.mock")
public class MockProperties {

    /** 总开关:关闭后所有工具都走真实接口,行为与未引入 mock 时完全一致 */
    private boolean enabled = false;

    /** 只 mock 这些工具(白名单);为空表示不启用白名单 */
    private List<String> include = new ArrayList<>();

    /** 这些工具走真实接口(黑名单) */
    private List<String> exclude = new ArrayList<>();

    /** 按工具名精确覆盖,优先级最高;value 为 true 走 mock,false 走真实接口 */
    private Map<String, Boolean> tools = new HashMap<>();

    /**
     * 判断某个工具是否走 mock 数据。
     *
     * <p>优先级:{@link #tools} 精确覆盖 &gt; {@link #exclude} &gt;
     * {@link #include}(非空则按白名单) &gt; {@link #enabled}。</p>
     *
     * @param toolName {@code @McpTool} 的 name,形如 {@code overview:getBasicInfo}
     * @return true 表示返回 mock 数据,false 表示调真实接口
     */
    public boolean shouldMock(String toolName) {
        if (tools != null && toolName != null) {
            Boolean exact = tools.get(toolName);
            if (exact != null) {
                return exact;
            }
        }
        if (toolName != null) {
            if (exclude != null && exclude.contains(toolName)) {
                return false;
            }
            if (include != null && !include.isEmpty()) {
                return include.contains(toolName);
            }
        }
        return enabled;
    }
}
