package com.hx.mcpsidecar.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hx.mcpsidecar.scene.SceneActions;
import com.hx.mcpsidecar.scene.SceneDispatcher;
import com.hx.mcpsidecar.scene.SceneOutcome;
import com.hx.mcpsidecar.scene.SceneSession;
import com.hx.mcpsidecar.scene.SceneSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 场景通道联调接口 —— 手动触发动作下发，不用等 LLM。
 *
 * <p>和 {@code SceneMcp} 走的是同一条 {@link SceneDispatcher#dispatch} 链路，
 * 所以词表校验、破坏性闸门、连不上就立刻失败这些行为完全一致。区别只是：
 * 那边的 sessionId 从 {@code X-Session-Id} header 来（给模型用的），
 * 这边从参数来（给人用的，可以随手改）。</p>
 *
 * <p>典型用法：</p>
 * <pre>
 *   # 1. 看现在哪个页面连着，拿到 sessionId
 *   GET  /scene/sessions
 *
 *   # 2. 浏览器直接点，让那个页面切到 3 楼
 *   GET  /scene/dispatch?sessionId=dev-1&amp;action=scene_switch_floor&amp;params={"floor":3}
 *
 *   # 3. 看词表（哪些动作名合法、哪些是破坏性的）
 *   GET  /scene/actions
 * </pre>
 *
 * <p><b>注意暴露面</b>：这是无鉴权接口，能驱动任何已连接页面。它没有引入新能力 ——
 * 同端口的 {@code /mcp/scene} 本来就无鉴权且能做同样的事 —— 但生产环境如果不想留这个口子，
 * 记得连同 {@code /mcp/scene} 一起在网关层收掉。</p>
 */
@Slf4j
@RestController
@RequestMapping("/scene")
public class SceneTestController {

    private final SceneDispatcher dispatcher;
    private final SceneSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public SceneTestController(SceneDispatcher dispatcher, SceneSessionRegistry registry,
                               ObjectMapper objectMapper) {
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /** 当前连着的场景页面。联调第一步先看这个 —— sessionId 填错动作就发不出去。 */
    @GetMapping("/sessions")
    public List<Map<String, Object>> sessions() {
        return registry.connectedSessions().stream().map(this::describe).toList();
    }

    /** 动作词表。 */
    @GetMapping("/actions")
    public Map<String, Object> actions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", SceneActions.ALL.size());
        result.put("actions", new TreeSet<>(SceneActions.ALL));
        result.put("destructive", new TreeSet<>(SceneActions.DESTRUCTIVE));
        result.put("floorSemantics", SceneActions.FLOOR_SEMANTICS);
        return result;
    }

    /**
     * 下发动作（GET 版，方便浏览器地址栏 / Postman 直接点）。
     *
     * <p>会阻塞到页面回执或超时（{@code scene.action-timeout-ms}，默认 15s），
     * 返回的就是页面给的结果。</p>
     *
     * @param sessionId 不传则走单会话兜底 —— 只在恰好一个页面连着时才敢猜
     * @param params    JSON 字符串，如 {@code {"floor":3}}
     */
    @GetMapping("/dispatch")
    public ResponseEntity<Map<String, Object>> dispatchByGet(
        @RequestParam String action,
        @RequestParam(required = false) String sessionId,
        @RequestParam(required = false) String params) {

        return doDispatch(sessionId, action, parseParams(params));
    }

    /** 下发动作（POST 版，参数用 JSON body 传，写起来干净些）。 */
    @PostMapping("/dispatch")
    public ResponseEntity<Map<String, Object>> dispatch(@RequestBody DispatchRequest request) {
        if (request == null || request.action() == null || request.action().isBlank()) {
            return ResponseEntity.badRequest().body(failure(null, "action 不能为空"));
        }
        return doDispatch(request.sessionId(), request.action(), request.params());
    }

    /** params 写错时是人打错了字，不是服务端故障 —— 给 400 而不是 500。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> onBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(failure(null, e.getMessage()));
    }

    // ==================================================================

    private ResponseEntity<Map<String, Object>> doDispatch(
        String sessionId, String action, Map<String, Object> params) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("sessionId", sessionId == null ? "(未指定，走单会话兜底)" : sessionId);
        body.put("params", params);

        SceneOutcome outcome = dispatcher.dispatch(sessionId, action, params);
        body.put("ok", outcome.ok());
        body.put("state", outcome.state());
        if (outcome.error() != null) {
            body.put("error", outcome.error());
        }
        log.info("联调下发 {} sessionId={} -> {} {}", action, sessionId,
            outcome.state(), outcome.error() == null ? "" : outcome.error());

        // 动作没下发成功是调用方的问题（词表外 / 参数错 / 页面没连），用 4xx 更醒目
        return outcome.ok()
            ? ResponseEntity.ok(body)
            : ResponseEntity.status(409).body(body);
    }

    private Map<String, Object> describe(SceneSession session) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sessionId", session.getSessionId());
        summary.put("userId", session.getUserId());
        summary.put("pendingActions", session.pendingSnapshot().size());
        summary.put("sceneState", readTree(session.getSceneStateJson()));
        return summary;
    }

    private Map<String, Object> parseParams(String params) {
        if (params == null || params.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(params);
            if (!node.isObject()) {
                throw new IllegalArgumentException("params 要是一个 JSON 对象");
            }
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("params 不是合法 JSON 对象：" + params, e);
        }
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> failure(String action, String error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("action", action);
        body.put("error", error);
        return body;
    }

    /** 工具调用的参数集合，字段名跟 /ws/scene 下发帧里的 params 一致。 */
    public record DispatchRequest(String sessionId, String action, Map<String, Object> params) {
    }
}