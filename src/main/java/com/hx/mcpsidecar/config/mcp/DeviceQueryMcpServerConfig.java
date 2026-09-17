package com.hx.mcpsidecar.config.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.service.mcp.platform.PlatformDeviceQueryMcp;
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
 * 设备查询 MCP server:暴露 {@code /mcp/devicequery} 端点,下挂 {@link PlatformDeviceQueryMcp}
 * 中的四组工具:
 * <ol>
 *   <li>设备资产库查询({@code /deviceInfo/*}):设备清单、设备详情、资产统计;</li>
 *   <li>设备状态与类型统计:按类型/状态/空间/楼层等维度聚合,口径均为启用/停用;</li>
 *   <li>设备在线率:能耗在线表 + 安防厂商实时接口,各接口在线判定口径不同;</li>
 *   <li>厂商平台实时设备列表({@code /calldevice/*} 透传):大华/ITC/和信原始数据。</li>
 * </ol>
 *
 * <p>与已有的 {@code /mcp/device}(设备态势:分类/健康度/在线率等统计)有少量接口重叠,
 * 但定位不同 —— 那个查的是平台的统计数据,这个覆盖纳管资产库与厂商实时明细,
 * 所以单独开一个端点,工具名前缀也用 {@code device_query:} 而不是 {@code device:}。</p>
 *
 * <p>实现方式与其它模块一致:新建工具类包(service/mcp/platform)+ 本配置类,
 * 将工具类 {@link PlatformDeviceQueryMcp} 注册到该端点下,即可作为一个独立的
 * MCP server 对外提供服务,无需修改自动装配配置。</p>
 */
@Configuration
public class DeviceQueryMcpServerConfig {

    /**
     * 创建设备查询 MCP 的传输层 Provider,并绑定唯一的 HTTP 端点 {@code /mcp/devicequery}。
     *
     * <p>该端点使用 WebMvc 流式传输(MCP Streamable HTTP),客户端通过它发现并调用
     * 本模块下的全部工具。不同的 MCP server 端点互不影响。</p>
     *
     * @param objectMapper Jackson 对象映射器,用于 MCP 消息的序列化/反序列化
     * @return 设备查询模块的传输层 Provider
     */
    @Bean
    public WebMvcStreamableServerTransportProvider deviceQueryMcpTransport(ObjectMapper objectMapper) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint("/mcp/devicequery")
                .build();
    }

    /**
     * 注册 {@code /mcp/devicequery} 端点的路由函数,交给 Spring MVC 处理。
     *
     * @param transport 设备查询模块的传输层 Provider(见 {@link #deviceQueryMcpTransport})
     * @return 该 MCP 端点的路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> deviceQueryMcpRouter(
            @Qualifier("deviceQueryMcpTransport") WebMvcStreamableServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    /**
     * 创建设备查询 MCP server,将 {@link PlatformDeviceQueryMcp} 中所有
     * {@code @McpTool} 注解的工具注册到 {@code /mcp/devicequery} 端点。
     *
     * <p>后续如需新增设备查询工具,只需在
     * {@link PlatformDeviceQueryMcp} 中增加 {@code @McpTool} 方法,无需改动本配置。</p>
     *
     * @param transport              设备查询模块的传输层 Provider
     * @param platformDeviceQueryMcp 设备查询工具集 Bean(见 {@link PlatformDeviceQueryMcp})
     * @return 设备查询 MCP server,应用关闭时自动释放资源
     */
    @Bean(destroyMethod = "close")
    public McpSyncServer deviceQueryMcpServer(
            @Qualifier("deviceQueryMcpTransport") WebMvcStreamableServerTransportProvider transport,
            PlatformDeviceQueryMcp platformDeviceQueryMcp) {
        return McpServer.sync(transport)
                .serverInfo("mcp-devicequery", "1.0.0")
                .tools(SyncMcpAnnotationProviders.toolSpecifications(List.of(platformDeviceQueryMcp)))
                .build();
    }
}