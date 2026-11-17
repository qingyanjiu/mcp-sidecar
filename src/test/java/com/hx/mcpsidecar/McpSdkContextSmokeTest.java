package com.hx.mcpsidecar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.ai.mcp.server.enabled=false"
        })
@ActiveProfiles("test")
class McpSdkContextSmokeTest {

    @Test
    void contextLoads() {
    }
}
