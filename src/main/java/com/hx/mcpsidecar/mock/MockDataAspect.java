package com.hx.mcpsidecar.mock;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * mock 开关切面:拦下所有 {@link MockData} 标注的 MCP 工具方法,
 * 按 {@link MockProperties} 的配置决定返回 mock 数据还是放行走真实接口。
 *
 * <p>被 {@code @MockData} 标注的方法会因此被 Spring 包一层 CGLIB 代理。
 * MCP 框架侧(spring-ai 的 {@code AnnotationProviderUtil})注册工具时走的是
 * {@code AopUtils.getTargetClass(bean)},注解发现不受代理影响;
 * 调用侧是 {@code Method.invoke(proxyBean)},会正常进到本切面。</p>
 */
@Slf4j
@Aspect
@Component
public class MockDataAspect {

    private final MockProperties mockProperties;
    private final MockDataLoader mockDataLoader;

    public MockDataAspect(MockProperties mockProperties, MockDataLoader mockDataLoader) {
        this.mockProperties = mockProperties;
        this.mockDataLoader = mockDataLoader;
    }

    @Around("@annotation(mockData)")
    public Object around(ProceedingJoinPoint pjp, MockData mockData) throws Throwable {
        Method method = resolveMethod(pjp);
        String toolName = resolveToolName(method);
        if (!mockProperties.shouldMock(toolName)) {
            return pjp.proceed();
        }
        Class<?> owner = AopUtils.getTargetClass(pjp.getTarget());
        String key = mockData.value().isEmpty() ? method.getName() : mockData.value();
        String data = mockDataLoader.get(owner, key);
        log.info("MCP工具[{}] 返回mock数据(mock/{}.json 的 {})", toolName, owner.getSimpleName(), key);
        return data;
    }

    /**
     * 取目标类上被调用的方法。代理场景下 {@code getSignature()} 给的是目标类方法,
     * 这里再兜一层 {@code getMostSpecificMethod} 防止拿到接口/父类上的声明而读不到 {@link McpTool}。
     */
    private static Method resolveMethod(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Object target = pjp.getTarget();
        if (target == null) {
            return method;
        }
        return AopUtils.getMostSpecificMethod(method, AopUtils.getTargetClass(target));
    }

    /**
     * 工具名优先取 {@code @McpTool} 的 name(形如 {@code overview:getBasicInfo}),
     * 取不到时退回方法名。
     */
    private static String resolveToolName(Method method) {
        McpTool mcpTool = AnnotatedElementUtils.findMergedAnnotation(method, McpTool.class);
        if (mcpTool != null && !mcpTool.name().isEmpty()) {
            return mcpTool.name();
        }
        return method.getName();
    }
}
