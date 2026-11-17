package com.hx.mcpsidecar;

import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.logging.LogManager;

@SpringBootApplication(
	exclude = { DruidDataSourceAutoConfigure.class,
		DataSourceAutoConfiguration.class }
)
@ComponentScan("com.hx.mcpsidecar")
@EnableScheduling
@EnableCaching
@Slf4j
public class McpSidecarBootstrap extends LogManager {

	static {
		// MCP SDK 0.18.x 默认按规范校验工具名（仅允许字母、数字、下划线、中划线和点）。
		// 本服务的工具名沿用历史前缀 device: / device_query: 等，严格校验会在建链时直接抛错。
		// 工具名完全由服务端代码定义、不来自用户输入，这里降级为告警以保持兼容；
		// 后续批量重命名工具并同步 agentic-app 的 MCP 配置后即可删除本段。
		// 允许通过 -Dio.modelcontextprotocol.strictToolNameValidation=true 显式恢复严格校验。
		if (System.getProperty("io.modelcontextprotocol.strictToolNameValidation") == null) {
			System.setProperty("io.modelcontextprotocol.strictToolNameValidation", "false");
		}
	}

	private static String[] args;
	private static ConfigurableApplicationContext context;

	public static void main(String[] args) {
		try {
			McpSidecarBootstrap.args = args;
			McpSidecarBootstrap.context = SpringApplication.run(McpSidecarBootstrap.class, args);
		} catch (Exception e) {
			log.error("项目启动时出现问题: \n{}\n退出启动... ", e.getMessage());
			if (context != null && context.isActive()) {
				SpringApplication.exit(context);
			}
		}
	}
	// 项目重启
	public static void restart() {
		context.close();
		McpSidecarBootstrap.context = SpringApplication.run(McpSidecarBootstrap.class, args);
	}

}
