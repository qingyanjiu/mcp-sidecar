package com.hx.mcpsidecar.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.hx.mcpsidecar.config.RestConfig;
import com.hx.mcpsidecar.model.McpServerProperties;
import com.hx.mcpsidecar.service.mcp.platform.PlatformApiCallServiceImpl;
import com.hx.mcpsidecar.service.mcp.platform.PlatformDeviceQueryMcp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlatformDeviceQueryMcp} 的测试。
 *
 * <p>第一个用例是纯 Mockito 单测，不联网；第二个用例是集成测试，
 * 真实调用本地平台 {@code http://localhost:9500/platform}，会走
 * {@link PlatformApiCallServiceImpl} 的登录 / Bearer token 流程。
 * 本地平台没起时自动跳过。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备查询MCP")
class McpTest {

    private static final String BASE_URL = "http://platform.test/platform";
    private static final String LOCAL_PLATFORM_BASE_URL = "http://localhost:9500/platform";
    private static final String LOCAL_PLATFORM_HOST = "localhost";
    private static final int LOCAL_PLATFORM_PORT = 9500;

    @Mock
    private IApiCallService apiCallService;

    @InjectMocks
    private PlatformDeviceQueryMcp platformDeviceQueryMcp;

    // ==================================================================
    // 单元测试：不联网，验证 URL 拼接
    // ==================================================================

    @Test
    @DisplayName("countByDeviceType 不传 spaceId 时，GET URL 不拼 spaceId 参数")
    void countByDeviceType_withoutSpaceId_omitsSpaceIdQueryParam() {
        when(apiCallService.getBaseUrl()).thenReturn(BASE_URL);
        List<Map<String, Object>> platformData = List.of(
            Map.of("count", 3, "deviceType", "3", "deviceTypeName", "监控")
        );
        when(apiCallService.doGetCall(anyString())).thenReturn(platformData);

        String result = platformDeviceQueryMcp.countByDeviceType(null);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiCallService).doGetCall(urlCaptor.capture());

        // 不传参数时 URL 里既没有问号，也没有 spaceId=null
        assertEquals(BASE_URL + PlatformDeviceQueryMcp.TYPE_NUM_STATISTIC_URL, urlCaptor.getValue());
        assertFalse(urlCaptor.getValue().contains("spaceId"));

        // 平台返回的数据原样透传成 JSON
        assertEquals(JSON.toJSONString(platformData), result);
    }

    // ==================================================================
    // 集成测试：真实调用本地平台，自动登录拿 token
    // ==================================================================

    @Test
    @Tag("integration")
    @DisplayName("countByDeviceType 真实调用本地平台（自动登录 + Bearer token）")
    void countByDeviceType_callsLocalPlatformWithAuth() {
        assumeTrue(isTcpReachable(LOCAL_PLATFORM_HOST, LOCAL_PLATFORM_PORT),
            "localhost:9500 未启动，跳过集成测试");

        PlatformApiCallServiceImpl realApi = new PlatformApiCallServiceImpl();
        ReflectionTestUtils.setField(realApi, "restTemplate", new RestConfig().restTemplate());
        ReflectionTestUtils.setField(realApi, "mcpApiProperties", localPlatformProperties());

        PlatformDeviceQueryMcp realMcp = new PlatformDeviceQueryMcp();
        ReflectionTestUtils.setField(realMcp, "platformApiCallServiceImpl", realApi);

        String result;
        try {
            result = realMcp.countByDeviceType(null);
        } catch (RestClientException e) {
            // 不把异常原样抛出：登录 URL 里带账号密码，异常消息会包含完整 URL
            fail("调用本地平台失败（HTTP 层），状态/响应见 surefire 日志: " + e.getClass().getSimpleName());
            return;
        } catch (IllegalArgumentException e) {
            fail("登录或接口调用失败：" + e.getMessage());
            return;
        }

        assertNotNull(result, "接口应返回数据");
        assertNotEquals("null", result, "接口不应返回 JSON null");

        JSONArray array;
        try {
            array = JSON.parseArray(result);
        } catch (RuntimeException e) {
            fail("返回值不是 JSON 数组，可能没有通过认证（拿到的是登录页）");
            return;
        }
        assertNotNull(array, "接口应返回 JSON 数组");
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private static McpServerProperties localPlatformProperties() {
        Map<String, Object> platformConfig = new HashMap<>();
        platformConfig.put("baseUrl", LOCAL_PLATFORM_BASE_URL);

        McpServerProperties properties = new McpServerProperties();
        properties.setServers(Map.of("platform", platformConfig));
        return properties;
    }

    private static boolean isTcpReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
