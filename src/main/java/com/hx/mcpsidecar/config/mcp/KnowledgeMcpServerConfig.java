package com.hx.mcpsidecar.config.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.service.mcp.ragflow.RagflowKnowledgeMcp;
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
 * 知识库检索 MCP server：暴露 {@code /mcp/knowledge} 端点，
 * 包含 RAGFlow 提供的知识库检索、知识库列表工具。
 *
 * 装配方式与 PersonStatusMcpServerConfig 等既有模块完全一致（三件套）：
 *   1. Transport  - Streamable HTTP 传输提供者，绑定本模块端点路径
 *   2. Router     - 把端点注册进 Spring MVC 路由
 *   3. McpServer  - 同步 MCP 服务实例，挂上本模块的工具类
 *
 * 注意：本项目关闭了全局 MCP 自动装配（见 application.yml 的 spring.ai.mcp.server.enabled=false），
 * 每个模块都要在这里手动注册，Bean 名必须全局唯一（用 knowledgeMcp 前缀做限定符区分）。
 */
@Configuration
public class KnowledgeMcpServerConfig {

    /**
     * 本模块的传输提供者，对外端点为 /mcp/knowledge。
     * 客户端连接地址即 http://<sidecar主机>:2222/mcp/knowledge（Streamable HTTP）。
     */
    @Bean
    public WebMvcStreamableServerTransportProvider knowledgeMcpTransport(ObjectMapper objectMapper) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint("/mcp/knowledge")
                .build();
    }

    /**
     * 把上面的传输提供者挂进 MVC 路由，让 /mcp/knowledge 真正可访问。
     * Qualifier 必须写：16+ 个模块都有同类型的 Transport/Router Bean，按名字区分。
     */
    @Bean
    public RouterFunction<ServerResponse> knowledgeMcpRouter(
            @Qualifier("knowledgeMcpTransport") WebMvcStreamableServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    /**
     * 知识库 MCP 服务实例：注册 RagflowKnowledgeMcp 里的全部 @McpTool 工具。
     * destroyMethod = "close" 保证应用关闭时优雅释放 MCP 会话资源。
     */
    @Bean(destroyMethod = "close")
    public McpSyncServer knowledgeMcpServer(
            @Qualifier("knowledgeMcpTransport") WebMvcStreamableServerTransportProvider transport,
            RagflowKnowledgeMcp ragflowKnowledgeMcp) {
        return McpServer.sync(transport)
                .serverInfo("mcp-knowledge", "1.0.0")
                .tools(SyncMcpAnnotationProviders.toolSpecifications(List.of(ragflowKnowledgeMcp)))
                .build();
    }
}
