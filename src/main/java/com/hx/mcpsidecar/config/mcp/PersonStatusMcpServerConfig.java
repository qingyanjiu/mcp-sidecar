package com.hx.mcpsidecar.config.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.service.mcp.platform.PlatformPersonStatusMcp;
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
 * 人员态势 MCP server：暴露 {@code /mcp/personStatus} 端点，
 * 包含平台提供的今日态势、人员流动等工具。
 */
@Configuration
public class PersonStatusMcpServerConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider personStatusMcpTransport(ObjectMapper objectMapper) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint("/mcp/personStatus")
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> personStatusMcpRouter(
            @Qualifier("personStatusMcpTransport") WebMvcStreamableServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer personStatusMcpServer(
            @Qualifier("personStatusMcpTransport") WebMvcStreamableServerTransportProvider transport,
            PlatformPersonStatusMcp platformPersonStatusMcp) {
        return McpServer.sync(transport)
                .serverInfo("mcp-person-status", "1.0.0")
                .tools(SyncMcpAnnotationProviders.toolSpecifications(List.of(platformPersonStatusMcp)))
                .build();
    }
}
