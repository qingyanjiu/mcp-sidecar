package com.hx.mcpsidecar.service.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.model.McpServerProperties;
import com.hx.mcpsidecar.model.PlatformResponse;
import com.hx.mcpsidecar.service.AbstractApiCallService;
import com.hx.mcpsidecar.service.IApiCallService;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Map;

@Slf4j
@Service
public class PlatformApiCallServiceImpl extends AbstractApiCallService implements IApiCallService<PlatformResponse> {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private McpServerProperties mcpApiProperties;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getBaseUrl() {
        String baseUrl = mcpApiProperties.getServers().get(getServerName()).get("baseUrl").toString();
        return baseUrl;
    }

    @Override
    public void login() {
        URI uri = URI.create(getBaseUrl() + "/login" + "?username=hexinadmin&password=123456&login_type=1");
        ResponseEntity<PlatformResponse> responseEntity = restTemplate.postForEntity(uri, null, PlatformResponse.class);
        PlatformResponse resp = responseEntity.getBody();
        Assert.isTrue(resp.getData() != null && resp.getStatus().equals("1"), "登录请求失败");
        String accessToken = ((Map)resp.getData()).get("token").toString();
        long timestamp = new Date().getTime();
        tokenMap.put("token", accessToken);
        tokenMap.put("expire", timestamp + getTokenExpiration());
    }

    @Override
    public Object doGetCall(String url) {
        HttpHeaders headers = initHeaderAuthorization();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        log.info("GET请求[{}]成功获取到数据", url);
        PlatformResponse platformResp = objectMapper.convertValue(response.getBody(), PlatformResponse.class);
        return platformResp.getData();
    }

    @Override
    public Object doPostCall(String url, Map<String, Object> data) {
        HttpHeaders headers = initHeaderAuthorization();
        // 封装请求
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(data, headers);
        // 发送 POST
        ResponseEntity<Map> response =
            restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        PlatformResponse platformResp = objectMapper.convertValue(response.getBody(), PlatformResponse.class);
        return platformResp.getData();
    }
}
