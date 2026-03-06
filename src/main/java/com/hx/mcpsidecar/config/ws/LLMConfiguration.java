package com.hx.mcpsidecar.config.ws;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LLMConfiguration {

    private int limitSize;

    private Map<String, String> wsPath;

    private Map<String, String> auth;
}
