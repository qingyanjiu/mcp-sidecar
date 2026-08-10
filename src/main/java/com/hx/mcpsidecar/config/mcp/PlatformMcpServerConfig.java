package com.hx.mcpsidecar.config.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.service.mcp.platform.PlatformPathFindMcp;
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

/**
 * platform 模块 MCP server:暴露 {@code /mcp/platform} 端点,只含路径规划类工具。
 */
@Configuration
public class PlatformMcpServerConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider platformMcpTransport(ObjectMapper objectMapper) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint("/mcp/platform")
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> platformMcpRouter(
            @Qualifier("platformMcpTransport") WebMvcStreamableServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer platformMcpServer(
            @Qualifier("platformMcpTransport") WebMvcStreamableServerTransportProvider transport,
            PlatformPathFindMcp platformPathFindMcp) {
        return McpServer.sync(transport)
                .serverInfo("mcp-platform", "1.0.0")
                .tools(SyncMcpAnnotationProviders.toolSpecifications(List.of(platformPathFindMcp)))
                .build();
    }
}
