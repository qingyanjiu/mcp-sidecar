package com.hx.mcpsidecar.config.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.service.mcp.platform.PlatformFireMcp;
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
 * 消防态势 MCP server:暴露 {@code /mcp/fire} 端点,
 * 包含平台提供的消防设备台账、设备告警统计、实时告警、月度报修工具。
 *
 * <p>实现方式与其它模块一致:新建工具类包(service/mcp/platform)+ 本配置类,
 * 将工具类 {@link PlatformFireMcp} 注册到该端点下,即可作为一个独立的
 * MCP server 对外提供服务,无需修改自动装配配置。</p>
 */
@Configuration
public class FireMcpServerConfig {

    /**
     * 创建消防态势 MCP 的传输层 Provider,并绑定唯一的 HTTP 端点 {@code /mcp/fire}。
     *
     * <p>该端点使用 WebMvc 流式传输(MCP Streamable HTTP),客户端通过它发现并调用
     * 本模块下的全部工具。不同的 MCP server 端点互不影响。</p>
     *
     * @param objectMapper Jackson 对象映射器,用于 MCP 消息的序列化/反序列化
     * @return 消防态势模块的传输层 Provider
     */
    @Bean
    public WebMvcStreamableServerTransportProvider fireMcpTransport(ObjectMapper objectMapper) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint("/mcp/fire")
                .build();
    }

    /**
     * 注册 {@code /mcp/fire} 端点的路由函数,交给 Spring MVC 处理。
     *
     * @param transport 消防态势模块的传输层 Provider(见 {@link #fireMcpTransport})
     * @return 该 MCP 端点的路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> fireMcpRouter(
            @Qualifier("fireMcpTransport") WebMvcStreamableServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    /**
     * 创建消防态势 MCP server,将 {@link PlatformFireMcp} 中所有
     * {@code @McpTool} 注解的工具注册到 {@code /mcp/fire} 端点。
     *
     * <p>后续如需新增消防态势工具,只需在 {@link PlatformFireMcp} 中
     * 增加 {@code @McpTool} 方法,无需改动本配置。</p>
     *
     * @param transport        消防态势模块的传输层 Provider
     * @param platformFireMcp 消防态势工具集 Bean(含设备台账、告警统计、实时告警、月度报修工具)
     * @return 消防态势 MCP server,应用关闭时自动释放资源
     */
    @Bean(destroyMethod = "close")
    public McpSyncServer fireMcpServer(
            @Qualifier("fireMcpTransport") WebMvcStreamableServerTransportProvider transport,
            PlatformFireMcp platformFireMcp) {
        return McpServer.sync(transport)
                .serverInfo("mcp-fire", "1.0.0")
                .tools(SyncMcpAnnotationProviders.toolSpecifications(List.of(platformFireMcp)))
                .build();
    }
}
