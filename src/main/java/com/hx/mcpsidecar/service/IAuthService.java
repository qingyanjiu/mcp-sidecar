package com.hx.mcpsidecar.service;

/**
 * 认证服务，请求主服务的认证接口，通过逻辑判断当前token是否可用
 */
public interface IAuthService {

    /**
     * 判断token是否可用
     * @param token
     * @return
     */
    boolean validateToken(String token, String uerId);
}
