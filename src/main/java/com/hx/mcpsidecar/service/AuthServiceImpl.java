package com.hx.mcpsidecar.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements IAuthService {

    @Value("${llm.auth.url}")
    private String authUrl;

    @Autowired
    private IApiCallService platformApiCallServiceImpl;

    /**
     * 验证用户是否登录， token和userid是否匹配
     * @param token
     * @param uerId
     * @return
     */
    @Override
    public boolean validateToken(String token, String uerId) {
        String requestUrl = String.format(authUrl, token, uerId);
        Object respCode = platformApiCallServiceImpl.doGetCall(requestUrl);
        if (Integer.toString(HttpStatus.OK.value()).equals(respCode.toString())) {
            return true;
        }
        return false;
    }
}
