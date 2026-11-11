package com.hx.mcpsidecar.config.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.service.mcp.scene.SceneMcp;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;
import java.util.Map;

/**
 * 场景控制模块 MCP server：暴露 {@code /mcp/scene}，只含 {SceneMcp} 里的 19 个工具。
 *
 * <p>与 {@code /mcp/utils}、{@code /mcp/pathFind} 并列，同进程同端口。
 * 动作帧不走这里 —— 那是 {@code /ws/scene} 的活，MCP 只负责"让 LLM 能调"。</p>
 */
@Configuration
public class SceneMcpServerConfig {

    /** 传输层元数据键，与 {@code SceneMcp} 读取的键一致。 */
    private static final String KEY_SESSION_ID = "sessionId";

    /** agent 侧按用户注入的会话标识 header（方案 §2.8 推荐方案）。 */
    private static final String HEADER_SESSION_ID = "X-Session-Id";

    @Bean
    public WebMvcStreamableServerTransportProvider sceneMcpTransport(ObjectMapper objectMapper) {
        return WebMvcStreamableServerTransportProvider.builder()
            .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
            .mcpEndpoint("/mcp/scene")
            // 把 HTTP header 提进传输层元数据，工具里通过 McpSyncRequestContext.transportContext() 读。
            // 这样 sessionId 对工具签名零侵入，LLM 看不见也填不了（方案 §2.8）。
            .contextExtractor(request -> {
                String sessionId = request.headers().firstHeader(HEADER_SESSION_ID);
                if (sessionId == null || sessionId.isBlank()) {
                    return McpTransportContext.EMPTY;
                }
                return McpTransportContext.create(Map.of(KEY_SESSION_ID, sessionId));
            })
            .build();
    }

    @Bean
    public RouterFunction<ServerResponse> sceneMcpRouter(
            @Qualifier("sceneMcpTransport") WebMvcStreamableServerTransportProvider transport) {
        // /mcp/** 只监听内网端口（2223），认证交给网络边界；公网 2222 不开放 MCP。
        return transport.getRouterFunction();
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer sceneMcpServer(
            @Qualifier("sceneMcpTransport") WebMvcStreamableServerTransportProvider transport,
            SceneMcp sceneMcp) {
        return McpServer.sync(transport)
                .serverInfo("mcp-scene", "1.0.0")
                .tools(SyncMcpAnnotationProviders.toolSpecifications(List.of(sceneMcp)))
                .build();
    }
}