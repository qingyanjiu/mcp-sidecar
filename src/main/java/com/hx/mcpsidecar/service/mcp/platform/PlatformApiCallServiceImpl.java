package com.hx.mcpsidecar.service.mcp.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.model.McpServerProperties;
import com.hx.mcpsidecar.model.PlatformResponse;
import com.hx.mcpsidecar.service.AbstractApiCallService;
import com.hx.mcpsidecar.service.IApiCallService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    @Override
    public void login() {
        URI uri = URI.create(getBaseUrl() + "/login" + "?username=hexinadmin&password=Hxkj@2026&login_type=1&captcha_code=d0080921765faab7b6a4d264dcf43f01");
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
        ResponseEntity<Map> response = super.get(url);
        log.info("GET请求[{}]成功获取到数据", url);
        PlatformResponse platformResp = objectMapper.convertValue(response.getBody(), PlatformResponse.class);
        return platformResp.getData();
    }

    @Override
    public Object doGetCallWithTokenInHeader(String url, String token) {
        ResponseEntity<Map> response = super.getWithTokenInHeader(url, token);
        log.info("GET无登录请求[{}]成功获取到数据", url);
        PlatformResponse platformResp = objectMapper.convertValue(response.getBody(), PlatformResponse.class);
        return platformResp.getData();
    }

    @Override
    public Object doPostCall(String url, Map<String, Object> data) {
        ResponseEntity<Map> response = super.post(url, data);
        PlatformResponse platformResp = objectMapper.convertValue(response.getBody(), PlatformResponse.class);
        return platformResp.getData();
    }
}
