package com.hx.mcpsidecar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 公司后台返回数据格式封装类
 */

// 平台返回体偶尔会带 errorCode 等未知字段（即使值为 null 也会触发反序列化报错），
// 加上忽略未知字段，保证对已有字段（data/msg/status）的解析不受影响
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlatformResponse {
    private Object data;

    private String msg;

    private String status;
}
