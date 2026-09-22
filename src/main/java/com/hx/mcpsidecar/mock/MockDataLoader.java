package com.hx.mcpsidecar.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * mock 数据加载器:启动时把 {@code classpath:mock/&#42;&#42;/&#42;.json} 全部读进内存。
 *
 * <p>约定 <b>文件名 = 工具类名</b>(不含 {@code .json}),以 {@code mock/platform/PlatformOverviewMcp.json}
 * 为例,它服务于 {@code com.hx.mcpsidecar.service.mcp.platform.PlatformOverviewMcp}。
 * 文件内容是「方法名 -&gt; 该方法的返回数据」的映射:</p>
 *
 * <pre>{@code
 * {
 *   "getBasicInfo": { "centerArea": 286000, "iotDeviceCount": 12480 },
 *   "getDeviceHealth": [ { "name": "消防", "score": 92.5 } ]
 * }
 * }</pre>
 *
 * <p>取值语义:JSON 值是字符串时原样返回文本(应对返回自然语言的工具),
 * 其余类型统一返回序列化后的 JSON 文本。两者都是 {@code String},
 * 与 {@code @McpTool} 方法的返回类型一致。</p>
 */
@Slf4j
@Component
public class MockDataLoader {

    /** mock 数据文件位置,新增模块时按包名建子目录即可,无需改代码 */
    private static final String MOCK_LOCATION = "classpath*:mock/**/*.json";
    private static final String JSON_SUFFIX = ".json";

    private final ObjectMapper objectMapper;

    /** 工具类名 -> (mock 键 -> 数据文本) */
    private final Map<String, Map<String, String>> mockByClassName = new LinkedHashMap<>();

    public MockDataLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 扫描并加载全部 mock 数据文件。幂等,重复调用会重新加载。
     */
    @PostConstruct
    public void loadAll() {
        mockByClassName.clear();
        int itemCount = 0;
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(MOCK_LOCATION);
            for (Resource resource : resources) {
                String className = classNameOf(resource);
                Map<String, String> data = readFile(resource);
                if (mockByClassName.put(className, data) != null) {
                    throw new IllegalStateException("mock 数据文件名重复: " + className
                            + JSON_SUFFIX + ", 文件名必须与工具类名一一对应");
                }
                itemCount += data.size();
            }
        } catch (IOException e) {
            throw new IllegalStateException("加载 mock 数据失败, 位置: " + MOCK_LOCATION, e);
        }
        log.info("mock数据加载完成: {} 个文件, {} 条数据 -> {}", mockByClassName.size(), itemCount, MOCK_LOCATION);
    }

    /**
     * 取某个工具类某个方法的 mock 数据。
     *
     * @param owner 工具类(传目标类即可,切面会做代理解包)
     * @param key   mock 键,通常是方法名
     * @return 可直接作为字符串返回的数据:JSON 对象/数组返回序列化文本,JSON 字符串返回原文
     * @throws IllegalStateException 文件或键缺失时抛出。这里刻意不兜底回退到真实接口,
     *                               mock 分支上「悄悄打了平台后端」远比直接报错危险
     */
    public String get(Class<?> owner, String key) {
        String className = owner.getSimpleName();
        Map<String, String> data = mockByClassName.get(className);
        if (data == null) {
            throw new IllegalStateException("缺少mock数据文件 mock/" + className + JSON_SUFFIX
                    + ", 该文件由 " + owner.getName() + " 上 @MockData 标注的方法自动对应");
        }
        String value = data.get(key);
        if (value == null) {
            throw new IllegalStateException("mock数据文件 mock/" + className + JSON_SUFFIX
                    + " 中缺少键 " + key + ", 现有键: " + data.keySet());
        }
        return value;
    }

    /**
     * 某个工具类已备好的 mock 键集合,供一致性校验使用。
     */
    public Map<String, String> dataOf(Class<?> owner) {
        return mockByClassName.getOrDefault(owner.getSimpleName(), Collections.emptyMap());
    }

    /**
     * 已加载的 mock 数据文件对应的类名。
     */
    public java.util.Set<String> loadedClassNames() {
        return Collections.unmodifiableSet(mockByClassName.keySet());
    }

    private String classNameOf(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null || !filename.endsWith(JSON_SUFFIX)) {
            throw new IllegalStateException("mock 数据文件名非法: " + resource);
        }
        return filename.substring(0, filename.length() - JSON_SUFFIX.length());
    }

    private Map<String, String> readFile(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("mock 数据文件必须是「方法名 -> 数据」的 JSON 对象: " + resource);
            }
            Map<String, String> data = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                // 字符串原样返回(应对返回自然语言文本的工具),其余统一序列化
                data.put(field.getKey(), value.isTextual() ? value.asText() : value.toString());
            }
            return data;
        }
    }
}
