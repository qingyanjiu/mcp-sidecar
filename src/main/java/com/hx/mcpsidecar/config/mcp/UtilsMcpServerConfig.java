package com.hx.mcpsidecar.config.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.service.mcp.utils.TimeMcp;
import com.hx.mcpsidecar.service.mcp.utils.WeatherMcp;
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
 * utils 模块 MCP server:暴露 {@code /mcp/utils} 端点,只含时间、天气类工具。
 *
 * <p>新增模块的方式:新建工具类包 + 新建一个类似的 {@code XxxMcpServerConfig},
 * 改 endpoint、server 名、{@code toolObjects(...)} 列表即可,无需改自动装配。</p>
 */
@Configuration
public class UtilsMcpServerConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider utilsMcpTransport(ObjectMapper objectMapper) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint("/mcp/utils")
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> utilsMcpRouter(
            @Qualifier("utilsMcpTransport") WebMvcStreamableServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer utilsMcpServer(
            @Qualifier("utilsMcpTransport") WebMvcStreamableServerTransportProvider transport,
            TimeMcp timeMcp, WeatherMcp weatherMcp) {
        return McpServer.sync(transport)
                .serverInfo("mcp-utils", "1.0.0")
                .tools(SyncMcpAnnotationProviders.toolSpecifications(List.of(timeMcp, weatherMcp)))
                .build();
    }
}
