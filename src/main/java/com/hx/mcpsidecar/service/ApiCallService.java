package com.hx.mcpsidecar.service;

import java.util.Map;

/**
 * 通过Dify的API实现的部分统计接口
 */
public interface ApiCallService<T> {

    Object doGetCall(String url);

    Object doPostCall(String url, Map<String, Object> data);

    String getBaseUrl();
}