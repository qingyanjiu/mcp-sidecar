package com.hx.mcpsidecar.service.mcp.ragflow;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.model.McpServerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RAGFlow 客户端 - 响应解析")
class RagflowApiClientTest {

    @Test
    @DisplayName("外部响应由 Jackson 解析，嵌套字段仍可按 JSONObject 读取")
    void parsesResponseWithJackson() {
        RagflowApiClient client = new RagflowApiClient();
        RestTemplate restTemplate = mock(RestTemplate.class);
        McpServerProperties properties = new McpServerProperties();
        properties.setServers(Map.of("ragflow", Map.of(
                "baseUrl", "http://ragflow:9380",
                "apiKey", "test-key")));

        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "mcpApiProperties", properties);
        ReflectionTestUtils.setField(client, "objectMapper", new ObjectMapper());

        when(restTemplate.exchange(
                eq("http://ragflow:9380/api/v1/datasets?page_size=100"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(
                        "{\"code\":0,\"data\":[{\"id\":\"d1\",\"name\":\"制度\",\"document_count\":2}]}"));

        JSONObject resp = client.listDatasets();

        assertEquals(0, resp.getInteger("code"));
        JSONObject first = resp.getJSONArray("data").getJSONObject(0);
        assertEquals("d1", first.getString("id"));
        assertEquals(2, first.getIntValue("document_count"));
    }
}
