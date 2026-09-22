package com.hx.mcpsidecar.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.service.IApiCallService;
import com.hx.mcpsidecar.service.mcp.platform.PlatformOverviewMcp;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切面接线的集成测试:用真实 Spring 上下文验证 mock 方案赖以成立的两个前提。
 *
 * <p>{@code @MockData} 会让工具类被 CGLIB 代理,而 MCP 框架是自己扫注解、自己反射调方法的。
 * 这两件事能不能兼容,是整个方案最大的风险点,所以单独测:</p>
 * <ol>
 *   <li><b>代理不影响工具发现</b> —— {@code SyncMcpAnnotationProviders} 仍能在代理 bean 上
 *       扫出全部 {@code @McpTool},工具名、数量都不变;</li>
 *   <li><b>框架的反射调用会经过切面</b> —— 复刻框架的调用方式
 *       ({@code AopUtils.getTargetClass(bean)} 取方法 + {@code Method.invoke(代理bean)}),
 *       断言拿到 mock 数据,且桩后端<b>一次都没被调用</b>。</li>
 * </ol>
 *
 * <p>这里用的是真实的 {@link PlatformOverviewMcp},只把 {@code IApiCallService} 换成桩,
 * 桩会记录调用次数 —— 这是"确实没打后端"最直接的证据。</p>
 */
@DisplayName("mock切面接线")
class MockDataAspectWiringTest {

    private static final String MOCK_JSON = "{\"centerArea\":286000,\"iotDeviceCount\":13100}";
    private static final String REAL_JSON = "{\"real\":true}";

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class MockEnabledConfig {

        @Bean("platformApiCallServiceImpl")
        StubApiCallService platformApiCallServiceImpl() {
            return new StubApiCallService();
        }

        @Bean
        MockProperties mockProperties() {
            MockProperties properties = new MockProperties();
            properties.setEnabled(true);
            return properties;
        }

        @Bean
        MockDataLoader mockDataLoader() {
            return new StubMockDataLoader();
        }

        @Bean
        MockDataAspect mockDataAspect(MockProperties properties, MockDataLoader loader) {
            return new MockDataAspect(properties, loader);
        }

        @Bean
        PlatformOverviewMcp platformOverviewMcp() {
            return new PlatformOverviewMcp();
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class MockDisabledConfig extends MockEnabledConfig {

        @Bean
        @Override
        MockProperties mockProperties() {
            MockProperties properties = new MockProperties();
            properties.setEnabled(false);
            return properties;
        }
    }

    /** 不读资源文件,任何键都返回同一份数据 */
    static class StubMockDataLoader extends MockDataLoader {

        StubMockDataLoader() {
            super(new ObjectMapper());
        }

        @Override
        public void loadAll() {
            // 测试不需要真实数据文件
        }

        @Override
        public String get(Class<?> owner, String key) {
            return MOCK_JSON;
        }
    }

    /** 桩后端:只记录被调了几次,便于断言"mock 时确实没打后端" */
    static class StubApiCallService implements IApiCallService<Object> {

        final AtomicInteger callCount = new AtomicInteger();

        @Override
        public Object doGetCall(String url) {
            callCount.incrementAndGet();
            return Map.of("real", true);
        }

        @Override
        public Object doGetCallWithTokenInHeader(String url, String token) {
            callCount.incrementAndGet();
            return Map.of("real", true);
        }

        @Override
        public Object doPostCall(String url, Map<String, Object> data) {
            callCount.incrementAndGet();
            return Map.of("real", true);
        }

        @Override
        public String getBaseUrl() {
            return "http://stub-backend";
        }
    }

    @Test
    @DisplayName("工具类被代理后仍能被MCP框架发现")
    void toolsAreDiscoveredOnProxiedBean() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(MockEnabledConfig.class)) {
            PlatformOverviewMcp bean = ctx.getBean(PlatformOverviewMcp.class);
            assertTrue(AopUtils.isAopProxy(bean), "工具类应被 @MockData 触发 CGLIB 代理");

            Set<String> names = SyncMcpAnnotationProviders.toolSpecifications(List.of(bean)).stream()
                    .map(spec -> spec.tool().name())
                    .collect(Collectors.toSet());
            assertEquals(Set.of("overview:getBasicInfo", "overview:getDeviceHealth"), names,
                    "代理不应影响 @McpTool 的扫描结果");
        }
    }

    @Test
    @DisplayName("框架的反射调用经过切面,返回mock数据且不触达后端")
    void frameworkInvocationHitsAspect() throws Exception {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(MockEnabledConfig.class)) {
            PlatformOverviewMcp bean = ctx.getBean(PlatformOverviewMcp.class);
            StubApiCallService backend = ctx.getBean(StubApiCallService.class);

            assertEquals(MOCK_JSON, invokeLikeFramework(bean, "getBasicInfo"));
            assertEquals(0, backend.callCount.get(), "命中mock时不应调用后端接口");
        }
    }

    @Test
    @DisplayName("工具调用入口(含结果序列化)返回mock数据的文本内容")
    void toolCallHandlerReturnsMockText() {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(MockEnabledConfig.class)) {
            PlatformOverviewMcp bean = ctx.getBean(PlatformOverviewMcp.class);
            SyncToolSpecification spec = SyncMcpAnnotationProviders.toolSpecifications(List.of(bean)).stream()
                    .filter(s -> "overview:getBasicInfo".equals(s.tool().name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("没注册出 overview:getBasicInfo 工具"));

            McpSchema.CallToolResult result = spec.callHandler()
                    .apply(null, new McpSchema.CallToolRequest("overview:getBasicInfo", Map.of()));

            assertEquals(Boolean.FALSE, result.isError(), "工具调用不应报错");
            List<McpSchema.Content> content = result.content();
            assertTrue(!content.isEmpty() && content.get(0) instanceof McpSchema.TextContent,
                    "应返回文本内容, 实际: " + content);
            assertEquals(MOCK_JSON, ((McpSchema.TextContent) content.get(0)).text());
        }
    }

    @Test
    @DisplayName("关掉总开关后确实回到原有方法体")
    void disabledSwitchFallsBackToRealBody() throws Exception {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(MockDisabledConfig.class)) {
            PlatformOverviewMcp bean = ctx.getBean(PlatformOverviewMcp.class);
            StubApiCallService backend = ctx.getBean(StubApiCallService.class);

            assertEquals(REAL_JSON, invokeLikeFramework(bean, "getBasicInfo"));
            assertEquals(1, backend.callCount.get(), "关掉开关后应该调用一次后端接口");
        }
    }

    /**
     * 复刻 {@code AbstractMcpToolMethodCallback#callMethod} 的调用方式:
     * 方法取自目标类(框架走 {@code AopUtils.getTargetClass}),调在代理 bean 上。
     */
    private static Object invokeLikeFramework(Object bean, String methodName) throws Exception {
        Method method = AopUtils.getTargetClass(bean).getMethod(methodName);
        assertTrue(method.isAnnotationPresent(McpTool.class), methodName + " 应带 @McpTool");
        assertTrue(method.isAnnotationPresent(MockData.class), methodName + " 应带 @MockData");
        method.setAccessible(true);
        return method.invoke(bean);
    }
}
