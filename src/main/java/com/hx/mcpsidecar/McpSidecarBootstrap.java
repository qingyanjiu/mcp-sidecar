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
