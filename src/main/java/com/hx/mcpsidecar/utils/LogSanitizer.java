package com.hx.mcpsidecar.utils;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * 日志脱敏工具：避免 token、密码、API Key 等凭证被写进日志。
 *
 * <p>覆盖两类常见形态：
 * <ul>
 *   <li>URL query：{@code ?token=xxx&userId=1}、{@code &api_key=xxx}</li>
 *   <li>Authorization 头：{@code Bearer xxx}</li>
 * </ul>
 */
public final class LogSanitizer {

    /** query 中的敏感字段，只保留字段名和分隔符，值统一替换为 *** */
    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)([?&](?:token|access_token|refresh_token|authorization|password|api[_-]?key)=)[^&#\\s]*");

    /** Authorization: Bearer xxx */
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+");

    private LogSanitizer() {
    }

    public static String maskUri(URI uri) {
        return uri == null ? null : mask(uri.toString());
    }

    public static String maskUri(String url) {
        return mask(url);
    }

    /** 对任意日志文本做脱敏，null 原样返回。 */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = SENSITIVE_QUERY.matcher(text).replaceAll("$1***");
        return BEARER_TOKEN.matcher(masked).replaceAll("$1***");
    }
}
