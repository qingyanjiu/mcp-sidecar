package com.hx.mcpsidecar.mock;

import com.hx.mcpsidecar.service.mcp.platform.PlatformEnergyMcp;
import com.hx.mcpsidecar.service.mcp.platform.PlatformOverviewMcp;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * mock 切面与开关配置的单元测试,不起 Spring 容器。
 */
@DisplayName("mock开关切面")
class MockDataAspectTest {

    private static final String REAL_RESULT = "真实接口返回";
    private static final String MOCK_RESULT = "{\"centerArea\":286000}";
    private static final String BASIC_INFO_TOOL = "overview:getBasicInfo";
    private static final String ENERGY_TOOL = "energy:getOverallEnergyConsu";

    private MockProperties properties;
    private MockDataLoader loader;
    private MockDataAspect aspect;

    @BeforeEach
    void setUp() {
        properties = new MockProperties();
        loader = mock(MockDataLoader.class);
        aspect = new MockDataAspect(properties, loader);
    }

    @Nested
    @DisplayName("开关优先级")
    class ShouldMock {

        @Test
        @DisplayName("总开关关闭时全部走真实接口")
        void disabledByDefault() {
            properties.setEnabled(false);
            assertFalse(properties.shouldMock(BASIC_INFO_TOOL));
        }

        @Test
        @DisplayName("总开关打开时默认走mock")
        void enabledGlobally() {
            properties.setEnabled(true);
            assertTrue(properties.shouldMock(BASIC_INFO_TOOL));
        }

        @Test
        @DisplayName("exclude优先级高于总开关")
        void excludeWins() {
            properties.setEnabled(true);
            properties.setExclude(List.of(BASIC_INFO_TOOL));
            assertFalse(properties.shouldMock(BASIC_INFO_TOOL));
            assertTrue(properties.shouldMock(ENERGY_TOOL));
        }

        @Test
        @DisplayName("include非空时按白名单,未命中的走真实接口")
        void includeActsAsWhitelist() {
            properties.setEnabled(true);
            properties.setInclude(List.of(BASIC_INFO_TOOL));
            assertTrue(properties.shouldMock(BASIC_INFO_TOOL));
            assertFalse(properties.shouldMock(ENERGY_TOOL));
        }

        @Test
        @DisplayName("include为空时不启用白名单")
        void emptyIncludeIsNotAWhitelist() {
            properties.setEnabled(true);
            properties.setInclude(List.of());
            assertTrue(properties.shouldMock(ENERGY_TOOL));
        }

        @Test
        @DisplayName("tools精确覆盖优先级最高,可反向开启或关闭")
        void exactOverrideWins() {
            properties.setEnabled(true);
            properties.setExclude(List.of(BASIC_INFO_TOOL));
            properties.setTools(Map.of(BASIC_INFO_TOOL, true));
            assertTrue(properties.shouldMock(BASIC_INFO_TOOL));

            properties.setEnabled(false);
            properties.setTools(Map.of(BASIC_INFO_TOOL, false, ENERGY_TOOL, true));
            assertFalse(properties.shouldMock(BASIC_INFO_TOOL));
            assertTrue(properties.shouldMock(ENERGY_TOOL));
        }
    }

    @Nested
    @DisplayName("切面拦截")
    class Around {

        @Test
        @DisplayName("命中mock时不调用真实方法")
        void servesMockData() throws Throwable {
            properties.setEnabled(true);
            when(loader.get(PlatformOverviewMcp.class, "getBasicInfo")).thenReturn(MOCK_RESULT);
            ProceedingJoinPoint pjp = joinPoint(PlatformOverviewMcp.class, "getBasicInfo");

            Object result = aspect.around(pjp, mockDataOf(PlatformOverviewMcp.class, "getBasicInfo"));

            assertEquals(MOCK_RESULT, result);
            verify(pjp, never()).proceed();
        }

        @Test
        @DisplayName("未命中mock时透传真实方法结果")
        void passesThroughToRealCall() throws Throwable {
            properties.setEnabled(true);
            properties.setExclude(List.of(BASIC_INFO_TOOL));
            ProceedingJoinPoint pjp = joinPoint(PlatformOverviewMcp.class, "getBasicInfo");

            Object result = aspect.around(pjp, mockDataOf(PlatformOverviewMcp.class, "getBasicInfo"));

            assertEquals(REAL_RESULT, result);
            verify(pjp).proceed();
            verify(loader, never()).get(any(), anyString());
        }

        @Test
        @DisplayName("总开关关闭时即便备了数据也走真实接口")
        void disabledGloballyGoesReal() throws Throwable {
            properties.setEnabled(false);
            ProceedingJoinPoint pjp = joinPoint(PlatformEnergyMcp.class, "getOverallEnergyConsu");

            Object result = aspect.around(pjp, mockDataOf(PlatformEnergyMcp.class, "getOverallEnergyConsu"));

            assertEquals(REAL_RESULT, result);
            verify(pjp).proceed();
        }

        @Test
        @DisplayName("tools精确覆盖可让单个工具走mock")
        void exactOverrideServesMock() throws Throwable {
            properties.setEnabled(false);
            properties.setTools(Map.of(ENERGY_TOOL, true));
            when(loader.get(eq(PlatformEnergyMcp.class), anyString())).thenReturn(MOCK_RESULT);
            ProceedingJoinPoint pjp = joinPoint(PlatformEnergyMcp.class, "getOverallEnergyConsu");

            Object result = aspect.around(pjp, mockDataOf(PlatformEnergyMcp.class, "getOverallEnergyConsu"));

            assertEquals(MOCK_RESULT, result);
            verify(pjp, never()).proceed();
        }
    }

    private static ProceedingJoinPoint joinPoint(Class<?> targetClass, String methodName) throws Throwable {
        Method method = targetClass.getMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getTarget()).thenReturn(targetClass.getDeclaredConstructor().newInstance());
        when(pjp.proceed()).thenReturn(REAL_RESULT);
        return pjp;
    }

    private static MockData mockDataOf(Class<?> targetClass, String methodName) throws Exception {
        return targetClass.getMethod(methodName).getAnnotation(MockData.class);
    }
}
