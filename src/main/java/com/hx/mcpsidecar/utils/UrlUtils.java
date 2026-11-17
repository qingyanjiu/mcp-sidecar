package com.hx.mcpsidecar.utils;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 内部 URL 拼接工具。
 *
 * <p>所有来自客户端 / 大模型参数的 userId、sessionId、type、code 等，拼进 URL 前必须编码，
 * 否则 {@code &}、{@code /}、{@code ?} 会改变请求参数或路径。
 */
public final class UrlUtils {

    private UrlUtils() {
    }

    /**
     * 追加一个 query 参数。值为空时原样返回，不产生 {@code ?key=} 这种空条件。
     */
    public static String appendQueryParam(String url, String name, String value) {
        if (!StringUtils.hasText(value)) {
            return url;
        }
        char separator = url.indexOf('?') >= 0 ? '&' : '?';
        return url + separator + name + "=" + encodeQueryParam(value.trim());
    }

    /** query 参数值编码（application/x-www-form-urlencoded，空格编码为 +）。 */
    public static String encodeQueryParam(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 路径段编码，斜杠会被编码成 %2F，不会形成新的路径层级。 */
    public static String encodePathSegment(String value) {
        return value == null ? "" : UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
