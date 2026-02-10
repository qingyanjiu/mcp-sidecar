package com.hx.mcpsidecar.service;

import com.hx.mcpsidecar.model.McpServerProperties;
import com.hx.mcpsidecar.model.PlatformResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ApiCallServiceImpl extends AbstractApiCallService implements ApiCallService<PlatformResponse> {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private McpServerProperties mcpApiProperties;

    @Override
    public void login() {
        String baseUrl = mcpApiProperties.getServers().get(getServerName()).get("baseUrl").toString();
        URI uri = URI.create(baseUrl + "/login" + "?username=hexinadmin&password=123456&login_type=1");
        ResponseEntity<PlatformResponse> responseEntity = restTemplate.postForEntity(uri, null, PlatformResponse.class);
        PlatformResponse resp = responseEntity.getBody();
        Assert.isTrue(resp.getData() != null && resp.getStatus().equals("1"), "登录请求失败");
        String accessToken = ((Map)resp.getData()).get("accessToken").toString();
        long timestamp = new Date().getTime();
        tokenMap.put("token", accessToken);
        tokenMap.put("expire", timestamp + getTokenExpiration());
    }

    @Override
    public Object doGetCall(String url, Class<PlatformResponse> clazz) {
        HttpHeaders headers = initHeaderAuthorization();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        PlatformResponse platformResp = (PlatformResponse) response.getBody();
        return platformResp.getData();
    }

    @Override
    public Object doPostCall(String url, Map<String, Object> data, Class<PlatformResponse> clazz) {
        HttpHeaders headers = initHeaderAuthorization();
        // 请求体（可以是 Map / DTO / JSON String）
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Tom");
        body.put("age", 18);
        // 封装请求
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        // 发送 POST
        ResponseEntity<PlatformResponse> response =
            restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                clazz
            );
        PlatformResponse platformResp = (PlatformResponse) response.getBody();
        return platformResp.getData();
    }
}
