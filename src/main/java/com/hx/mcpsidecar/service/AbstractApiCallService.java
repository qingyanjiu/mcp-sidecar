package com.hx.mcpsidecar.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Date;
import java.util.Map;

public abstract class AbstractApiCallService {

    // 暂存token数据，简单点就放在内存里了
    protected Map<String, Object> tokenMap = new java.util.concurrent.ConcurrentHashMap<>();

    // 应用名称，默认公司platform应用
    protected String getServerName() {
        return "platform";
    }

    // token过期时间, 默认半小时
    protected long getTokenExpiration() {
        return 1_800_000;
    }

    // 登录对应系统获取token
    public abstract void login();

    /**
     * 请求前在header中拼接认证token
     * @return
     */
    protected HttpHeaders initHeaderAuthorization() {
        String token = getToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    /**
     * 获取token
     */
    protected String getToken() {
        // 如果有token，判断是否过
        if (tokenMap.containsKey("token")) {
            String token = (String) tokenMap.get("token");
            long expire = (long) tokenMap.get("expire");
            long currentTime = new Date().getTime();
            // 过期重新取
            if (currentTime >= expire) {
                login();
                token = (String) tokenMap.get("token");
            }
            // 不过期，直接用
            return token;
        } else {
            // 如果没有token，说明还没有第一次获取token，直接获取
            login();
            return (String) tokenMap.get("token");
        }
    }
}
