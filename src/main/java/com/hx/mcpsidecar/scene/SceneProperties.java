package com.hx.mcpsidecar.scene;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 场景通道配置。全部有默认值，不配也能跑。
 */
@ConfigurationProperties(prefix = "scene")
@Data
@Component
public class SceneProperties {

    /**
     * 工具内等待页面 {@code action_result} 的上限（毫秒）。
     *
     * <p>方案 §2.5：前端执行是毫秒级，15s 已极宽。这一层必须小于 Python 侧 MCP client 的超时，
     * 否则外层先断，工具拿到的是"客户端已放弃"而不是干净的 timeout。</p>
     */
    private long actionTimeoutMs = 15_000L;

    /**
     * 同一 sessionId 重连时，是否把仍在途的动作帧重发给新连接。
     *
     * <p>方案 §2.3 的"把未完成的挂起项迁移过去"：不重发的话，刷新页面后没有任何东西会产生
     * {@code action_result}，在途工具调用只能白等到超时（即 §六 Phase 1 的验收项）。前端
     * {@code SceneExecutor} 明确"不做去重，收到就执行"，所以重发是协议允许的。</p>
     *
     * <p>代价：破坏性动作可能被执行两次。当前破坏性动作被 {@link #destructiveEnabled}
     * 挡着，17 个非破坏性动作都是幂等的（设楼层 / 显示 / 隐藏 / 聚焦）。</p>
     */
    private boolean redispatchOnReconnect = true;

    /**
     * 是否允许下发破坏性动作（{@code scene_set_area_state} / {@code scene_set_inspection_state}）。
     *
     * <p>方案 §4：Phase 4 接上确认卡片前不执行。false 时这两个工具直接返回"需确认、暂未开放"，
     * 不下发也不挂起。</p>
     */
    private boolean destructiveEnabled = false;

    /**
     * 工具拿不到 {@code X-Session-Id} 时，是否在当前恰好只有一条已连接会话的情况下自动路由过去。
     *
     * <p>为了 Phase 1 单用户联调不必等 Python 侧把 header 打通。**只有一条连接时才生效** ——
     * 两条以上直接报错，避免多用户时推错浏览器（方案 §2.8）。</p>
     */
    private boolean singleSessionFallback = true;

    /** 既没有连接、也没有在途挂起项的空壳会话保留时长（毫秒），到期回收。 */
    private long idleEvictMs = 30 * 60_000L;
}