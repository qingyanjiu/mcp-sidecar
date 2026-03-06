package com.hx.mcpsidecar.service;

import com.hx.mcpsidecar.config.ws.LLMConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
        if (Integer.toString(HttpStatus.OK.value()).equals(respCode.toString())) {
            return true;
        }
        return false;
    }
}
