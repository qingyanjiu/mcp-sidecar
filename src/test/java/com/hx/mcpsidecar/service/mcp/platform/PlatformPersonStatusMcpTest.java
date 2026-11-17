package com.hx.mcpsidecar.service.mcp.platform;

import com.hx.mcpsidecar.service.IApiCallService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("人员态势工具 - 响应解析")
class PlatformPersonStatusMcpTest {

    private static final String BASE = "http://localhost:9500/platform";

    @Test
    @DisplayName("直接读取平台返回的 Map，不再用 fastjson 二次解析")
    void readsNumbersFromMap() {
        PlatformPersonStatusMcp mcp = new PlatformPersonStatusMcp();
        IApiCallService<?> platformApi = mock(IApiCallService.class);
        when(platformApi.getBaseUrl()).thenReturn(BASE);
        when(platformApi.doGetCall(BASE + "/overview/getTodayPeople"))
                .thenReturn(Map.of("realTimeNum", 12, "jingNum", "3", "chuNum", 4.0));
        ReflectionTestUtils.setField(mcp, "platformApiCallServiceImpl", platformApi);

        assertEquals("实时在园人数：12人；今日进入人数：3人；今日离开人数：4人。",
                mcp.getTodayPersonnelAffairs());
    }

    @Test
    @DisplayName("字段缺失时按 0 处理，不抛异常")
    void missingFieldsFallbackToZero() {
        PlatformPersonStatusMcp mcp = new PlatformPersonStatusMcp();
        IApiCallService<?> platformApi = mock(IApiCallService.class);
        when(platformApi.getBaseUrl()).thenReturn(BASE);
        when(platformApi.doGetCall(BASE + "/overview/getTodayPeople")).thenReturn(Map.of());
        ReflectionTestUtils.setField(mcp, "platformApiCallServiceImpl", platformApi);

        assertEquals("实时在园人数：0人；今日进入人数：0人；今日离开人数：0人。",
                mcp.getTodayPersonnelAffairs());
    }
}
