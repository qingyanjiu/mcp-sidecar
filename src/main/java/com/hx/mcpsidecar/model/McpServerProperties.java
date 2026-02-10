package com.hx.mcpsidecar.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@ConfigurationProperties(prefix = "mcp")
@Data
@Component
public class McpServerProperties {
    /**
     * 对接的server列表
     */
    private Map<String, Map<String, Object>> servers;
}
