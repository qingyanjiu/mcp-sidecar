package com.hx.mcpsidecar.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "mcp")
@Data
public class McpServerProperties {
    /**
     * 对接的server列表
     */
    private Map<String, Map<String, Object>> servers;
}
