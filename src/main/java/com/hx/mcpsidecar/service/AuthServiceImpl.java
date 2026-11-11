package com.hx.mcpsidecar.service;

import com.hx.mcpsidecar.config.ws.LLMConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthServiceImpl implements IAuthService {

    @Autowired
    private LLMConfiguration llmConfiguration;

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
        String requestUrl = String.format(llmConfiguration.getAuth().get("url"), token, uerId);
        Object respCode = platformApiCallServiceImpl.doGetCallWithTokenInHeader(requestUrl, token);
        // 平台约定：data=200 代表 token 与 userId 匹配且登录态有效；401 是未登录/userId 不匹配。
        boolean valid = respCode != null && Integer.toString(HttpStatus.OK.value()).equals(respCode.toString());
        if (!valid) {
            log.warn("token校验未通过: userId={}, 平台返回data={}", uerId, respCode);
        }
        return valid;
    }
}
