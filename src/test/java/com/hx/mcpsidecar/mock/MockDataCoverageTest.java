package com.hx.mcpsidecar.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * mock 数据一致性校验:保证 {@code @McpTool} 方法、{@code @MockData} 注解、
 * {@code mock/platform/*.json} 三者严格对齐。
 *
 * <p>不起 Spring 容器(本项目启动依赖 postgres/redis/kafka),直接走
 * {@link MockDataLoader} 的加载逻辑。这条路是 mock 分支最容易出的问题
 * ——漏编一个方法、键名写错、JSON 语法错误——的主要防线。</p>
 */
@DisplayName("mock数据一致性校验")
class MockDataCoverageTest {

    /** 被 @MockData 标注了却没有对应数据的工具方法,逐条列出便于定位 */
    private static final List<String> MISSING_DATA = new ArrayList<>();

    /** 备了数据但源码里没有对应 @McpTool 方法的键 */
    private static final List<String> ORPHAN_DATA = new ArrayList<>();

    private static MockDataLoader loader;
    private static List<Class<?>> toolClasses;

    @BeforeAll
    static void setUp() {
        loader = new MockDataLoader(new ObjectMapper());
        loader.loadAll();
        toolClasses = scanToolClasses();
        assertFalse(toolClasses.isEmpty(), "没扫描到任何 platform 工具类,检查扫描路径");
    }

    @Test
    @DisplayName("每个 @McpTool 方法都标注了 @MockData")
    void everyToolMethodIsAnnotated() {
        Set<String> notAnnotated = new TreeSet<>();
        for (Class<?> clazz : toolClasses) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(McpTool.class) && !method.isAnnotationPresent(MockData.class)) {
                    notAnnotated.add(clazz.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertTrue(notAnnotated.isEmpty(),
                "以下 @McpTool 方法缺少 @MockData 注解: " + notAnnotated);
    }

    @Test
    @DisplayName("每个 @MockData 方法都能取到 mock 数据")
    void everyAnnotatedMethodHasMockData() {
        collectCoverageGaps();
        assertTrue(MISSING_DATA.isEmpty(), "以下工具方法没有对应的 mock 数据: " + MISSING_DATA);
    }

    @Test
    @DisplayName("mock数据文件里没有多余的键")
    void noOrphanMockData() {
        collectCoverageGaps();
        assertTrue(ORPHAN_DATA.isEmpty(), "以下 mock 数据没有对应的 @McpTool 方法: " + ORPHAN_DATA);
    }

    @Test
    @DisplayName("每个工具类都有同名 mock 数据文件")
    void everyToolClassHasDataFile() {
        Set<String> missingFiles = new TreeSet<>();
        for (Class<?> clazz : toolClasses) {
            if (!loader.loadedClassNames().contains(clazz.getSimpleName())) {
                missingFiles.add(clazz.getSimpleName());
            }
        }
        assertTrue(missingFiles.isEmpty(),
                "以下工具类缺少同名 mock 数据文件 mock/platform/<类名>.json: " + missingFiles);
    }

    @Test
    @DisplayName("mock数据文件文件名都能对应到真实存在的工具类")
    void everyDataFileMapsToAClass() {
        Set<String> classNames = new LinkedHashSet<>();
        toolClasses.forEach(c -> classNames.add(c.getSimpleName()));
        Set<String> orphans = new TreeSet<>();
        loader.loadedClassNames().stream().filter(name -> !classNames.contains(name)).forEach(orphans::add);
        assertTrue(orphans.isEmpty(), "以下 mock 数据文件找不到同名工具类: " + orphans);
    }

    @Test
    @DisplayName("工具数量与预期一致")
    void toolCount() {
        int count = 0;
        for (Class<?> clazz : toolClasses) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(MockData.class)) {
                    count++;
                }
            }
        }
        assertEquals(85, count,
                "platform 包下 @MockData 方法数应为 85, 实际 " + count + ", 新增/删除工具时请同步更新本断言");
    }

    /**
     * 双向比对:方法有数据、数据有方法。结果分别累积到两个静态列表,由各个测试方法断言。
     */
    private static void collectCoverageGaps() {
        if (!MISSING_DATA.isEmpty() || !ORPHAN_DATA.isEmpty()) {
            return;
        }
        for (Class<?> clazz : toolClasses) {
            Map<String, String> data = loader.dataOf(clazz);
            if (data.isEmpty()) {
                continue;
            }
            Set<String> keysOfMethods = new TreeSet<>();
            for (Method method : clazz.getDeclaredMethods()) {
                McpTool mcpTool = method.getAnnotation(McpTool.class);
                MockData mockData = method.getAnnotation(MockData.class);
                if (mcpTool == null || mockData == null) {
                    continue;
                }
                String key = mockData.value().isEmpty() ? method.getName() : mockData.value();
                keysOfMethods.add(key);
                if (!data.containsKey(key)) {
                    MISSING_DATA.add(mcpTool.name() + " (" + clazz.getSimpleName() + "#" + method.getName() + ")");
                }
            }
            for (String key : data.keySet()) {
                if (!keysOfMethods.contains(key)) {
                    ORPHAN_DATA.add(clazz.getSimpleName() + "#" + key);
                }
            }
        }
    }

    /**
     * 扫描 service.mcp.platform 下的工具类。跳过内部类(名含 {@code $})与无 {@link McpTool} 的类。
     */
    private static List<Class<?>> scanToolClasses() {
        List<Class<?>> classes = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:com/hx/mcpsidecar/service/mcp/platform/*.class");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || filename.contains("$")) {
                    continue;
                }
                String className = "com.hx.mcpsidecar.service.mcp.platform."
                        + filename.substring(0, filename.length() - ".class".length());
                Class<?> clazz = Class.forName(className);
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(McpTool.class)) {
                        classes.add(clazz);
                        break;
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("扫描 platform 工具类失败", e);
        }
        return classes;
    }
}
