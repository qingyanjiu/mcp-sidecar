package com.hx.mcpsidecar.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("内部 URL 拼接")
class UrlUtilsTest {

    @Test
    @DisplayName("query 参数中的 & / = 被编码，不会改变参数结构")
    void encodesQuerySpecialCharacters() {
        assertEquals("http://platform/api?code=a%26b%3Dc",
                UrlUtils.appendQueryParam("http://platform/api", "code", "a&b=c"));
    }

    @Test
    @DisplayName("已有 query 时用 & 追加，空值不产生空条件")
    void appendsToExistingQueryAndSkipsBlank() {
        String url = UrlUtils.appendQueryParam("http://platform/api", "type", "day");
        url = UrlUtils.appendQueryParam(url, "code", "A/1");
        assertEquals("http://platform/api?type=day&code=A%2F1", url);
        assertEquals("http://platform/api?type=day",
                UrlUtils.appendQueryParam("http://platform/api?type=day", "code", " "));
    }

    @Test
    @DisplayName("路径段中的 ../ 和 / 被编码")
    void encodesPathTraversal() {
        assertEquals("..%2F..%2Fadmin", UrlUtils.encodePathSegment("../../admin"));
    }
}
