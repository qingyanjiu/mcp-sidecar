package com.hx.mcpsidecar.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("日志脱敏")
class LogSanitizerTest {

    @Test
    @DisplayName("URL query 中的 token 被替换，其他参数保留")
    void masksTokenInUri() {
        String masked = LogSanitizer.maskUri(
                URI.create("http://127.0.0.1:2222/llm/chat?token=abc123&userId=u1"));
        assertEquals("http://127.0.0.1:2222/llm/chat?token=***&userId=u1", masked);
    }

    @Test
    @DisplayName("access_token / api_key / password 同样脱敏")
    void masksOtherSensitiveKeys() {
        assertEquals("http://host/x?access_token=***&api_key=***&password=***",
                LogSanitizer.maskUri("http://host/x?access_token=a&api_key=b&password=c"));
    }

    @Test
    @DisplayName("Authorization: Bearer 头脱敏")
    void masksBearerToken() {
        assertEquals("Authorization: Bearer ***",
                LogSanitizer.mask("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"));
    }

    @Test
    @DisplayName("null 与普通文本不报错")
    void handlesNullAndPlainText() {
        assertNull(LogSanitizer.mask(null));
        assertNull(LogSanitizer.maskUri((URI) null));
        assertEquals("普通日志", LogSanitizer.mask("普通日志"));
    }
}
