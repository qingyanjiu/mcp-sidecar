package com.hx.mcpsidecar.mock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记在 MCP 工具方法上,声明该方法备有 mock 数据。
 *
 * <p>打上本注解后,是否真的返回 mock 由配置 {@code mcp.mock.*} 决定
 * (见 {@link MockProperties}):配置命中时 {@link MockDataAspect} 直接返回
 * {@code mock/platform/&lt;类名&gt;.json} 里对应的数据,不再调用平台接口;
 * 未命中时按原逻辑调真实接口。</p>
 *
 * <p>用法:加在 {@code @McpTool} 之上即可,方法体不用动。</p>
 *
 * <pre>{@code
 * @MockData
 * @McpTool(name = "overview:getBasicInfo", description = "...")
 * public String getBasicInfo() {
 *     String url = platformApiCallServiceImpl.getBaseUrl() + OVERVIEW_BASIC_INFO_URL;
 *     Object result = platformApiCallServiceImpl.doGetCall(url);
 *     return JSON.toJSONString(result);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MockData {

    /**
     * mock 数据在 {@code mock/platform/&lt;类名&gt;.json} 里的键。
     * 默认取方法名,同一个类里方法名唯一,所以一般不用写。
     */
    String value() default "";
}
