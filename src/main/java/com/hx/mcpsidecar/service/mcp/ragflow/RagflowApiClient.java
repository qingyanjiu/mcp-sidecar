package com.hx.mcpsidecar.service.mcp.ragflow;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hx.mcpsidecar.model.McpServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAGFlow 知识库平台 API 客户端。
 *
 * 与 {@link com.hx.mcpsidecar.service.AbstractApiCallService} 那套"登录换 token"的
 * platform 接入方式不同：RAGFlow 使用静态 API Key 鉴权（网页"头像 → API"里创建），
 * 请求头固定带 Authorization: Bearer ragflow-xxx 即可，没有 login 流程，
 * 因此单独实现一个轻量客户端，不复用 AbstractApiCallService。
 *
 * 对接的是 RAGFlow 的 HTTP API（默认端口 9380），不是它的 MCP Server：
 *   - 检索:  POST {baseUrl}/api/v1/retrieval
 *   - 列库:  GET  {baseUrl}/api/v1/datasets
 * 完整接口见官方文档 https://ragflow.io/docs/http_api_reference
 *
 * 配置挂在 application-xxx.yml 的 mcp.servers.ragflow 节点下（复用 McpServerProperties）：
 *   mcp:
 *       servers:
 *           ragflow:
 *               baseUrl: http://<ragflow主机>:9380   # RAGFlow API 地址（必填）
 *               apiKey: ragflow-xxxxxxxx             # API Key（必填）
 *               datasetIds: ""                       # 默认检索的知识库ID，多个用英文逗号分隔；留空=全库检索（可选）
 *               topK: 8                              # 每次检索返回的文本块数量（可选，默认 8）
 *               similarityThreshold: 0.2             # 相似度阈值，太严格召回少、太宽松召回脏（可选，默认 0.2）
 */
@Slf4j
@Service
public class RagflowApiClient {

    /** 在 mcp.servers 配置里的服务名，对应 yml 中 mcp.servers.ragflow 节点 */
    private static final String SERVER_NAME = "ragflow";

    /** 检索接口路径 */
    public static final String RETRIEVAL_URL = "/api/v1/retrieval";

    /** 知识库列表接口路径 */
    public static final String DATASETS_URL = "/api/v1/datasets";

    /** 单个文本块内容最大保留长度。chunk 太长会撑爆大模型的上下文，超长部分截断 */
    private static final int MAX_CHUNK_CONTENT_LENGTH = 800;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private McpServerProperties mcpApiProperties;

    /**
     * 知识库检索。
     *
     * @param question   用户问题（自然语言）
     * @param datasetIds 指定检索的知识库ID列表；传 null/空集合时回退到 yml 里配置的 datasetIds，
     *                   两处都为空则不带 dataset_ids 字段，RAGFlow 会在全部知识库中检索
     * @return RAGFlow 原始响应 JSON，结构：{code:0, message:"", data:{chunks:[...], total:N, doc_aggs:[...]}}
     *         每个 chunk 含 content（文本内容）、document_keyword（来源文档名）、similarity（相似度）等字段
     * @throws RuntimeException RAGFlow 返回 code != 0 或网络调用失败时抛出，由上层工具方法兜底成友好文案
     */
    public JSONObject retrieval(String question, List<String> datasetIds) {
        // 组装请求体。RAGFlow 检索接口的关键参数：question 问题；dataset_ids 知识库范围；
        // top_k 返回条数；similarity_threshold 相似度门槛（低于该值的块不返回）
        Map<String, Object> body = new HashMap<>();
        body.put("question", question);
        body.put("top_k", getConfigInt("topK", 8));
        body.put("similarity_threshold", getConfigDouble("similarityThreshold", 0.2));

        List<String> ids = resolveDatasetIds(datasetIds);
        if (!ids.isEmpty()) {
            body.put("dataset_ids", ids);
        }
        // ids 为空时不放 dataset_ids 字段，语义 = 不限知识库、全库检索

        JSONObject resp = exchangeJson(HttpMethod.POST, RETRIEVAL_URL, body);
        assertSuccess(resp, "知识库检索");
        return resp;
    }

    /**
     * 列出当前 API Key 可见的全部知识库。
     *
     * @return RAGFlow 原始响应 JSON，data 为数组，每项含 id、name、description、document_count、chunk_num
     * @throws RuntimeException RAGFlow 返回 code != 0 或网络调用失败时抛出
     */
    public JSONObject listDatasets() {
        // page_size 上限 100，一次列全；后续知识库多了再考虑分页
        JSONObject resp = exchangeJson(HttpMethod.GET, DATASETS_URL + "?page_size=100", null);
        assertSuccess(resp, "知识库列表查询");
        return resp;
    }

    /**
     * 把工具入参的 datasetIds 与 yml 配置的 datasetIds 合并解析出最终检索范围。
     * 优先级：工具入参 > yml 配置；都为空则返回空列表（=全库检索）。
     */
    private List<String> resolveDatasetIds(List<String> datasetIds) {
        List<String> result = new ArrayList<>();
        if (datasetIds != null && !datasetIds.isEmpty()) {
            result.addAll(datasetIds);
            return result;
        }
        // yml 里 datasetIds 用英文逗号分隔，如 "abc123,def456"；空串/没配 = 不限定
        String configured = getConfigValue("datasetIds", "");
        if (StringUtils.hasText(configured)) {
            for (String id : configured.split(",")) {
                if (StringUtils.hasText(id)) {
                    result.add(id.trim());
                }
            }
        }
        return result;
    }

    /**
     * 统一的 HTTP 调用入口：带 Bearer Key 请求头，序列化/反序列化 JSON。
     *
     * @param method 请求方法（GET / POST）
     * @param url    完整请求路径（baseUrl + 接口路径）
     * @param body   请求体；GET 请求传 null
     * @return 响应体 JSON；HTTP 层失败（超时、连接拒绝、非 2xx）时抛异常并记日志
     */
    private JSONObject exchangeJson(HttpMethod method, String url, Map<String, Object> body) {
        String fullUrl = getBaseUrl() + url;
        try {
            log.info("RAGFlow调用 [{}] {}, 参数: {}", method, fullUrl, body);
            HttpHeaders headers = buildHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response =
                    restTemplate.exchange(fullUrl, method, request, String.class);
            // 用 String 接收再手动解析，避免 RAGFlow 偶发的 Content-Type 问题导致转换失败
            JSONObject resp = JSON.parseObject(response.getBody());
            log.info("RAGFlow调用 [{}] {} 成功, code={}", method, fullUrl, resp.getInteger("code"));
            return resp;
        } catch (Exception e) {
            log.error("RAGFlow调用 [{}] {} 失败: {}", method, fullUrl, e.getMessage(), e);
            throw new RuntimeException("调用RAGFlow服务失败: " + e.getMessage(), e);
        }
    }

    /**
     * RAGFlow 的应答约定：HTTP 200 但业务可能失败，业务结果看 body 里的 code 字段（0=成功）。
     */
    private void assertSuccess(JSONObject resp, String action) {
        Integer code = resp.getInteger("code");
        if (code == null || code != 0) {
            String message = resp.getString("message");
            log.error("RAGFlow{}失败, code={}, message={}", action, code, message);
            throw new RuntimeException(action + "失败: " + (StringUtils.hasText(message) ? message : "未知错误"));
        }
    }

    /**
     * 组装请求头。RAGFlow 不校验 key 内容本身，但要求非空的 Bearer token。
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + getConfigValue("apiKey", ""));
        return headers;
    }

    /** 截断超长文本，超出部分以「……(已截断)」结尾 */
    public String truncate(String text) {
        if (text == null || text.length() <= MAX_CHUNK_CONTENT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_CHUNK_CONTENT_LENGTH) + "……(已截断)";
    }

    /** 取 mcp.servers.ragflow 下的某个配置项（字符串） */
    private String getConfigValue(String key, String defaultValue) {
        Map<String, Object> config = getServerConfig();
        Object value = config.get(key);
        return value == null ? defaultValue : value.toString();
    }

    /** 取 mcp.servers.ragflow 下的某个配置项（int，带默认值） */
    private int getConfigInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getConfigValue(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("RAGFlow配置 {} 不是合法数字, 使用默认值 {}", key, defaultValue);
            return defaultValue;
        }
    }

    /** 取 mcp.servers.ragflow 下的某个配置项（double，带默认值） */
    private double getConfigDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(getConfigValue(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("RAGFlow配置 {} 不是合法数字, 使用默认值 {}", key, defaultValue);
            return defaultValue;
        }
    }

    /** 取 mcp.servers.ragflow 配置节点，没配置时给出明确报错，方便部署时排查 */
    private Map<String, Object> getServerConfig() {
        Map<String, Map<String, Object>> servers = mcpApiProperties.getServers();
        Map<String, Object> config = servers == null ? null : servers.get(SERVER_NAME);
        if (config == null) {
            throw new IllegalStateException(
                    "缺少 RAGFlow 配置，请在 application-xxx.yml 的 mcp.servers 下增加 " + SERVER_NAME + " 节点(baseUrl/apiKey)");
        }
        return config;
    }

    /** RAGFlow API 地址，如 http://10.0.0.5:9380 */
    private String getBaseUrl() {
        String baseUrl = getConfigValue("baseUrl", "");
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("RAGFlow 配置项 baseUrl 未填写");
        }
        // 去掉末尾多余的 /，拼接接口路径时统一格式
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
