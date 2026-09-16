package com.hx.mcpsidecar.scene;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hx.mcpsidecar.scene.SceneSession.PendingAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 动作下发 + 挂起 / 唤醒（方案 §2.5，Java 侧的核心）。
 *
 * <p>一次调用的形状：生成 {@code actionId} → 查连接（查不到立刻失败）→ 挂在 sessionId 上
 * → 推 {@code event:"action"} 帧 → 等 {@code action_result} → 换算成给 LLM 的返回值。</p>
 *
 * <p><b>关于阻塞：</b>本方法会阻塞调用线程直到回执或超时。MCP 传输层
 * （{@code WebMvcStreamableServerTransportProvider}）在 {@code ServerResponse.sse(...)} 的
 * 回调里同步 {@code .block()}，所以即便把 MCP server 换成 async、工具返回 {@code Mono}，
 * 占住的仍然是同一个 servlet 线程 —— 换不来任何非阻塞收益。因此这里直接同步等待，
 * 保持与既有两个 MCP server 一致的实现风格。超时上限见
 * {@link SceneProperties#getActionTimeoutMs()}。</p>
 */
@Slf4j
@Component
public class SceneDispatcher {

    /** {@code action_result} 里 error 字段的前缀，key 必须原样透给模型（方案 §2.5）。 */
    private static final String ERR_UNKNOWN_ACTION = "UNKNOWN_ACTION:";
    private static final String ERR_BAD_PARAM = "BAD_PARAM:";

    private final SceneSessionRegistry registry;
    private final SceneProperties properties;
    private final ObjectMapper objectMapper;

    public SceneDispatcher(SceneSessionRegistry registry, SceneProperties properties,
                           ObjectMapper objectMapper) {
        this.registry = registry;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 下发一个动作并等待回执。
     *
     * @param headerSessionId 来自 {@code X-Session-Id}，可能为 null（走单会话兜底）
     * @param params          下发到前端的参数；null 值会被整条丢掉，让前端用自己的默认值
     */
    public SceneOutcome dispatch(String headerSessionId, String name, Map<String, Object> params) {
        if (!SceneActions.isValid(name)) {
            // 正常路径走不到：工具名与词表同源。真出现说明代码改了词表没同步。
            log.error("拦下词表外的动作名: {}", name);
            return SceneOutcome.rejected("内部错误：动作 " + name + " 不在 sidecar 词表内");
        }

        if (SceneActions.DESTRUCTIVE.contains(name) && !properties.isDestructiveEnabled()) {
            return SceneOutcome.rejected(
                "该动作会改变现场状态，需要用户确认后才执行，当前阶段尚未开放");
        }

        SceneSession session = registry.resolve(headerSessionId);
        if (session == null) {
            // 故意不挂起：让 LLM 等一分钟才知道"页面没开"毫无价值（方案 §2.5）。
            return SceneOutcome.rejected(
                "场景页面未连接，无法执行。请提示用户先打开场景页");
        }

        String actionId = newActionId();
        Map<String, Object> cleanParams = dropNulls(params);
        PendingAction pending = new PendingAction(actionId, name, cleanParams);

        session.park(actionId, pending);
        try {
            session.send(actionFrame(actionId, session.getSessionId(), name, cleanParams));
        } catch (IOException e) {
            session.unpark(actionId);
            log.info("动作下发失败，连接已断开: sessionId={}, actionId={}, name={}",
                session.getSessionId(), actionId, name);
            return SceneOutcome.rejected("场景页面连接已断开，动作未下发。请提示用户重新打开场景页");
        }

        try {
            SceneOutcome outcome = pending.getFuture()
                .get(properties.getActionTimeoutMs(), TimeUnit.MILLISECONDS);
            if (outcome.ok()) {
                patchCachedFloor(session, name, cleanParams);
            }
            return outcome;
        } catch (TimeoutException e) {
            // 帧可能已发出（只是回执丢了），也可能还卡在缓冲区 —— 不能断言"没执行"（方案 §2.5 第 6 点）。
            log.info("动作超时未收到回执: sessionId={}, actionId={}, name={}, timeout={}ms",
                session.getSessionId(), actionId, name, properties.getActionTimeoutMs());
            return SceneOutcome.rejected("动作超时未收到页面回执，执行状态未知");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SceneOutcome.rejected("动作等待被中断");
        } catch (ExecutionException e) {
            log.error("动作等待异常: actionId={}, name={}", actionId, name, e);
            return SceneOutcome.rejected("动作等待异常：" + e.getCause());
        } finally {
            session.unpark(actionId);
        }
    }

    /**
     * 重连后把仍在途的动作帧重发给新连接（方案 §2.3）。
     *
     * <p>不重发的话，刷新页面后没有任何东西会产生 {@code action_result}，在途工具调用只能白等到超时。
     * 前端执行器明确不做去重、收到就执行，所以重发是协议允许的；代价是破坏性动作可能被执行两次，
     * 而破坏性动作当前被 {@link SceneProperties#isDestructiveEnabled()} 挡着。</p>
     */
    public void redispatchPending(SceneSession session) {
        if (!properties.isRedispatchOnReconnect()) {
            return;
        }
        for (PendingAction action : session.pendingSnapshot()) {
            if (action.getFuture().isDone()) {
                continue;
            }
            try {
                session.send(actionFrame(action.getActionId(), session.getSessionId(),
                    action.getName(), action.getParams()));
                log.info("重连后重发在途动作: sessionId={}, actionId={}, name={}",
                    session.getSessionId(), action.getActionId(), action.getName());
            } catch (IOException e) {
                log.warn("重连后重发动作失败: sessionId={}, actionId={}",
                    session.getSessionId(), action.getActionId());
                return;
            }
        }
    }

    /**
     * 乐观更新楼层缓存：本轮里切了楼层，后续节点再问"当前几楼"时不该拿到旧值，
     * 不能等页面下次上报（方案 §五"同轮内陈旧"）。
     *
     * <p>{@code scene_reset_all} 内部走 {@code showFloor(99)}，楼层不在 params 里，
     * 所以单独给它一个分支 —— 不刷的话清场之后模型问"现在几楼"会拿到清场前的值。</p>
     */
    private void patchCachedFloor(SceneSession session, String name, Map<String, Object> params) {
        if (SceneActions.RESET_ALL.equals(name)) {
            writeCachedFloor(session, SceneActions.FLOOR_WHOLE_BUILDING);
        } else {
            writeCachedFloor(session, params.get(SceneActions.PARAM_FLOOR));
        }
    }

    private void writeCachedFloor(SceneSession session, Object floor) {
        String raw = session.getSceneStateJson();
        if (floor == null || raw == null) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node instanceof ObjectNode objectNode) {
                objectNode.put("floor", objectMapper.valueToTree(floor));
                session.updateSceneState(objectMapper.writeValueAsString(objectNode));
            }
        } catch (Exception e) {
            // 缓存没解析成功不影响动作本身，只是 get_scene_state 会拿到旧快照。
            log.debug("刷新楼层缓存失败: sessionId={}", session.getSessionId(), e);
        }
    }

    private String actionFrame(String actionId, String sessionId, String name,
                               Map<String, Object> params) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("actionId", actionId);
        data.put("sessionId", sessionId);
        data.put("name", name);
        data.set("params", objectMapper.valueToTree(params));

        ObjectNode frame = objectMapper.createObjectNode();
        frame.put("event", "action");
        frame.set("data", data);
        return frame.toString();
    }

    /** 短 actionId，形如 {@code a-7c21}。前端原样回传，是唯一的关联键。 */
    private String newActionId() {
        return "a-" + Long.toHexString(ThreadLocalRandom.current().nextLong(0x1_0000_0000L) | 0x1_0000_0000L)
            .substring(1);
    }

    private Map<String, Object> dropNulls(Map<String, Object> params) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((key, value) -> {
                if (value != null) {
                    clean.put(key, value);
                }
            });
        }
        return clean;
    }

    /**
     * 把页面回的 {@code error} 码翻成模型能用的说明。
     *
     * <p>{@code BAD_PARAM:<key>} 里的 key <b>必须原样透出</b> —— 缺哪个参数是模型自我纠正的
     * 唯一线索（方案 §2.5 第 4 点）。</p>
     */
    public static String describeReject(String error) {
        if (error == null || error.isBlank()) {
            return "场景页面拒绝了该动作，但没有给出原因";
        }
        if (error.startsWith(ERR_UNKNOWN_ACTION)) {
            return "场景页面不认识动作 " + error.substring(ERR_UNKNOWN_ACTION.length())
                + "（前后端词表不同步，需要检查工具名）";
        }
        if (error.startsWith(ERR_BAD_PARAM)) {
            return "缺少必填参数 " + error.substring(ERR_BAD_PARAM.length());
        }
        return switch (error) {
            case "BAD_FRAME" -> "动作帧格式错误（缺少 actionId 或 name），属于内部协议问题";
            case "PLAYER_NOT_READY" -> "场景播放器尚未就绪，请提示用户稍后重试";
            default -> "场景页面拒绝了该动作：" + error;
        };
    }
}