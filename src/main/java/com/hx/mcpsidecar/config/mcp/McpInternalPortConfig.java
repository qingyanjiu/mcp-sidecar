package com.hx.mcpsidecar.config.mcp;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 给 {@code /mcp/**} 单独开一个只走容器网络的内网端口（默认 2223）。
 *
 * <p>docker-compose 里只 {@code expose} 不 {@code publish}，所以宿主机和公网都访问不到；
 * 只有同一容器的 {@code agentic-app} 能通过 {@code mcp-sidecar:2223} 调用。</p>
 *
 * <p>{@code server} 和 {@code local} 都启用，保证本地与线上端口分工一致：
 * MCP 一律走 2223，公网口 2222 只留 {@code /llm/**}、{@code /ws/**}。</p>
 *
 * <p>监听地址必须显式指定（SEC-08）：默认 {@code 127.0.0.1}，本地非容器运行时
 * 2223 不会出现在 {@code lsof} 的 {@code *} 上；容器内由 compose 注入
 * {@code MCP_INTERNAL_ADDRESS=0.0.0.0}，否则服务名无法访问。</p>
 */
@Configuration
@Profile({"server", "local"})
public class McpInternalPortConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> mcpInternalConnector(
            @Value("${mcp.internal-port:2223}") int internalPort,
            @Value("${mcp.internal-address:127.0.0.1}") String internalAddress) {
        return factory -> factory.addAdditionalTomcatConnectors(
                createConnector(internalPort, internalAddress));
    }

    private static Connector createConnector(int port, String address) {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(port);
        // Connector 没有 setAddress，Tomcat 通过 property 设置监听地址。
        connector.setProperty("address", address);
        return connector;
    }
}
