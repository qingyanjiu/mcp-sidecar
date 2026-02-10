package com.hx.mcpsidecar.model;

import lombok.Data;

/**
 * 公司后台返回数据格式封装类
 */

@Data
public class PlatformResponse {
    private Object data;

    private String msg;

    private String status;
}
